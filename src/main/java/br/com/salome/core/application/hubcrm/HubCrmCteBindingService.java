package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.CteQuoteMatcher;
import br.com.salome.core.domain.hubcrm.LegacyColeta;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuoteChain;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmPosAprovacaoProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Acompanha a cotação depois de aprovada e amarra o CT-e que aparecer, sem reaprovar nem alterar
 * valores no legado. Complementa o {@link HubCrmCteApprovalService}, que continua aprovando sozinho
 * quando o CT-e sai antes de alguém clicar em Aprovar.
 *
 * <p>Duas vias, nesta ordem:
 * <ol>
 *   <li><b>pela coleta</b> — o legado lança a coleta na aprovação ({@code coleta.idCotacao}) e o
 *       retorno dela gera o CT-e ({@code conhecimento.idColeta}). Vínculo exato, sem conferir
 *       valores;</li>
 *   <li><b>pelas regras de sempre</b> ({@link CteQuoteMatcher#evaluateForBinding}) — para o CT-e
 *       emitido no balcão, sem coleta.</li>
 * </ol>
 *
 * <p>O resultado alimenta a aba "Aprovação por CT-e" e segura a triagem do
 * {@link HubCrmAprovadaSemCteService}: cotação com CT-e amarrado, ou com coleta em andamento, não é
 * reprovada.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}"
        + " and ${salome.hub-crm.pos-aprovacao.binding-enabled:false}")
public class HubCrmCteBindingService {
    /** O prazo é em dias; quatro passagens por hora bastam e poupam o legado. */
    private static final Duration INTERVAL = Duration.ofMinutes(15);

    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final HubCrmAutoApprovalProperties autoApproval;
    private final Clock clock;
    private final AtomicReference<Instant> lastRun = new AtomicReference<>();

    @Autowired
    public HubCrmCteBindingService(HubCrmLegacyRepository legacy, HubCrmStore store,
            HubCrmAutoApprovalProperties autoApproval) {
        this(legacy, store, autoApproval, Clock.system(HubCrmCteApprovalService.LEGACY_ZONE));
    }

    HubCrmCteBindingService(HubCrmLegacyRepository legacy, HubCrmStore store,
            HubCrmAutoApprovalProperties autoApproval, Clock clock) {
        this.legacy = legacy;
        this.store = store;
        this.autoApproval = autoApproval;
        this.clock = clock;
    }

    /** Chamado a cada polling; roda de fato a cada 15 minutos. */
    public synchronized int bindApprovedQuotes() {
        Instant now = clock.instant();
        Instant previous = lastRun.get();
        if (previous != null && previous.isAfter(now.minus(INTERVAL))) return 0;
        lastRun.set(now);
        return bindNow();
    }

    /** Sem throttle: usado pela ação da tela e pelos testes. */
    public synchronized int bindNow() {
        LocalDate today = LocalDate.now(clock);
        LocalDate since = today.minusDays(HubCrmPosAprovacaoProperties.MAX_LOOKBACK_DAYS);
        List<LegacyQuote> approved = legacy.findApprovedQuotesSince(since);
        if (approved.isEmpty()) return 0;

        Map<Long, List<LegacyQuoteChain>> chains = chainsByQuote(approved);
        // Cópia própria: o que é amarrado agora entra aqui e não é reavaliado no mesmo ciclo.
        Set<Long> bound = new HashSet<>(store.quotesApprovedByCte());
        Map<String, List<LegacyCte>> ctesByPayer = freeCtesByPayer(since, approved, bound);

        int bindings = 0;
        for (LegacyQuote quote : approved) {
            try {
                bindings += track(quote, relevantChain(chains.get(quote.id())), bound, ctesByPayer, today);
            } catch (Exception exception) {
                // Isolado por cotação: sem gravar acompanhamento, é tentado de novo no próximo ciclo.
                store.recordEvent("quote:" + quote.id() + ":amarracao-falha", "COTACAO", quote.id(),
                        "AMARRACAO_CTE", "ERRO", null, exception.getMessage());
            }
        }
        return bindings;
    }

    /**
     * CT-es que ainda não estão amarrados a cotação nenhuma, por raiz de CNPJ do pagador. Só entram
     * os pagadores das cotações aprovadas — o resto do movimento do dia não interessa aqui.
     */
    private Map<String, List<LegacyCte>> freeCtesByPayer(LocalDate since, List<LegacyQuote> approved,
            Set<Long> bound) {
        Set<String> payers = approved.stream()
                .filter(quote -> quote.payerCnpj() != null && !quote.payerCnpj().isBlank())
                .map(quote -> CteQuoteMatcher.payerKey(quote.payerCnpj()))
                .collect(Collectors.toSet());
        if (payers.isEmpty()) return new HashMap<>();
        List<LegacyCte> ctes = legacy.findRecentCtes(since).stream()
                .filter(cte -> payers.contains(CteQuoteMatcher.payerKey(cte.payerCnpj())))
                .toList();
        if (ctes.isEmpty()) return new HashMap<>();
        Set<Long> used = store.cteMatchIds(ctes.stream().map(LegacyCte::id).toList());
        return ctes.stream()
                .filter(cte -> !used.contains(cte.id()))
                .collect(Collectors.groupingBy(cte -> CteQuoteMatcher.payerKey(cte.payerCnpj()), HashMap::new,
                        Collectors.toCollection(ArrayList::new)));
    }

    private int track(LegacyQuote quote, LegacyQuoteChain chain, Set<Long> bound,
            Map<String, List<LegacyCte>> ctesByPayer, LocalDate today) {
        LegacyColeta coleta = chain == null ? null : chain.coleta();
        int days = quote.statusAt() == null ? 0
                : (int) ChronoUnit.DAYS.between(quote.statusAt().toLocalDate(), today);
        String origin = store.changedByHub(quote.id()) ? "HUB" : "MANUAL";

        Optional<HubCrmStore.CteRef> cte = bound.contains(quote.id()) ? store.boundCte(quote.id())
                : Optional.empty();
        String binding = cte.isPresent() ? "CTE" : null;
        String detail = null;
        int bindings = 0;
        if (cte.isEmpty()) {
            Bound result = bind(quote, chain, ctesByPayer);
            if (result != null && result.conflict()) {
                // CT-e já amarrado a outra cotação: não sobrescreve e pede olho humano.
                store.upsertQuoteApproval(quote.id(), quote.responsible(), quote.statusAt(), origin,
                        coleta == null ? null : coleta.id(), coleta == null ? null : coleta.status(),
                        null, null, "REVISAO", days, result.detail());
                store.recordEvent("cte:" + result.cte().id() + ":amarracao", "CTE", result.cte().id(),
                        "AMARRACAO_CTE", "REVISAO", null, result.detail());
                return 0;
            }
            if (result != null) {
                cte = Optional.of(HubCrmStore.CteRef.of(result.cte()));
                binding = result.source();
                detail = result.detail();
                bindings = 1;
                bound.add(quote.id());
                dropCte(ctesByPayer, quote, result.cte());
                store.recordEvent("quote:" + quote.id() + ":cte-amarrada", "COTACAO", quote.id(),
                        "AMARRACAO_CTE", "PROCESSADO", detail, null);
            }
        }

        if (cte.isEmpty() && coleta != null && coleta.alive()) {
            detail = "Coleta " + coleta.id() + " em andamento (" + coleta.status() + ")";
        }
        store.upsertQuoteApproval(quote.id(), quote.responsible(), quote.statusAt(), origin,
                coleta == null ? null : coleta.id(), coleta == null ? null : coleta.status(),
                cte.orElse(null), binding, cte.isPresent() ? "AMARRADA" : "SEM_CTE", days, detail);
        return bindings;
    }

    /** Procura o CT-e da cotação: primeiro pela coleta, depois pelas regras de sempre. */
    private Bound bind(LegacyQuote quote, LegacyQuoteChain chain, Map<String, List<LegacyCte>> ctesByPayer) {
        if (chain != null && chain.cteId() != null) {
            LegacyCte cte = legacy.findCtesByIds(List.of(chain.cteId())).stream().findFirst().orElse(null);
            if (cte != null) {
                return record(quote, cte, "AMARRADA_COLETA", "COLETA", "Coleta " + chain.coletaId()
                        + " (" + chain.coleta().status() + ") → CT-e " + cte.label());
            }
        }
        if (quote.payerCnpj() == null || quote.payerCnpj().isBlank()) return null;
        for (LegacyCte cte : ctesByPayer.getOrDefault(CteQuoteMatcher.payerKey(quote.payerCnpj()), List.of())) {
            var match = CteQuoteMatcher.evaluateForBinding(cte, List.of(quote), autoApproval.windowDays());
            if (match.outcome() != CteQuoteMatcher.Outcome.APROVAR) continue;
            return record(quote, cte, "AMARRADA_CTE", "CTE",
                    "CT-e " + cte.label() + " amarrado à cotação aprovada: " + match.criteria());
        }
        return null;
    }

    private Bound record(LegacyQuote quote, LegacyCte cte, String status, String source, String detail) {
        if (store.recordCteBinding(cte, quote, status, detail, null)) {
            return new Bound(cte, source, detail, false);
        }
        return new Bound(cte, source, "CT-e " + cte.label() + " já amarrado a outra cotação; a cotação "
                + quote.id() + " segue sem CT-e", true);
    }

    private void dropCte(Map<String, List<LegacyCte>> ctesByPayer, LegacyQuote quote, LegacyCte used) {
        ctesByPayer.computeIfPresent(CteQuoteMatcher.payerKey(quote.payerCnpj()),
                (key, list) -> list.stream().filter(cte -> cte.id() != used.id()).toList());
    }

    private Map<Long, List<LegacyQuoteChain>> chainsByQuote(List<LegacyQuote> approved) {
        return legacy.findQuoteChains(approved.stream().map(LegacyQuote::id).toList()).stream()
                .collect(Collectors.groupingBy(LegacyQuoteChain::quoteId, HashMap::new,
                        Collectors.toCollection(ArrayList::new)));
    }

    /** Entre as coletas da cotação vale a que virou CT-e; depois a que ainda está viva; depois a última. */
    static LegacyQuoteChain relevantChain(List<LegacyQuoteChain> chains) {
        if (chains == null || chains.isEmpty()) return null;
        return chains.stream()
                .max(Comparator.comparing((LegacyQuoteChain c) -> c.cteId() != null)
                        .thenComparing(LegacyQuoteChain::aliveColeta)
                        .thenComparing(LegacyQuoteChain::coletaId))
                .orElse(null);
    }

    private record Bound(LegacyCte cte, String source, String detail, boolean conflict) {}
}
