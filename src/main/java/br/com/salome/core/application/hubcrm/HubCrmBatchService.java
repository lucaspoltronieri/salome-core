package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.CteQuoteMatcher;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Lote único de limpeza das cotações (pedido do Lucas em 11/09/2026):
 * <ol>
 *   <li>aprova toda cotação que tem CT-e correspondente (mesma regra do
 *       {@link CteQuoteMatcher}, CT-es desde 2021);</li>
 *   <li>cotação ABERTA criada antes do corte e sem CT-e vira NÃO APROVADA com motivo Preço.</li>
 * </ol>
 * Roda em segundo plano (ler os CT-es desde 2021 leva minutos). Em simulação não grava nada.
 * O ganho/perda no ArpaSuite sai pelo {@link HubCrmQuoteSyncService} nos ciclos seguintes.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class HubCrmBatchService {
    private static final Logger log = LoggerFactory.getLogger(HubCrmBatchService.class);
    static final LocalDate CTE_SINCE = LocalDate.of(2021, 1, 1);
    static final LocalDate QUOTES_SINCE = LocalDate.of(2000, 1, 1);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final LegacyQuoteApprovalWriter writer;
    private final HubCrmAutoApprovalProperties properties;
    private final Clock clock;
    private final AtomicReference<BatchState> state = new AtomicReference<>(BatchState.idle());

    @Autowired
    public HubCrmBatchService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmAutoApprovalProperties properties) {
        this(legacy, store, writer, properties, Clock.systemDefaultZone());
    }

    HubCrmBatchService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmAutoApprovalProperties properties, Clock clock) {
        this.legacy = legacy;
        this.store = store;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    /** Dispara o lote em segundo plano; se já houver um rodando, só devolve o estado atual. */
    public synchronized BatchState start(boolean execute, LocalDate cutoff) {
        if (state.get().running()) return state.get();
        state.set(BatchState.started(execute, cutoff, Instant.now(clock)));
        Thread.ofVirtual().name("hub-crm-lote").start(() -> run(execute, cutoff));
        return state.get();
    }

    public BatchState status() {
        return state.get();
    }

    void run(boolean execute, LocalDate cutoff) {
        List<BatchItem> items = new ArrayList<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        try {
            phase("Lendo cotações não aprovadas", items, totals);
            List<LegacyQuote> quotes = legacy.findApprovableQuotes(QUOTES_SINCE);
            phase("Lendo CT-es desde " + BR.format(CTE_SINCE) + " (" + quotes.size() + " cotações)", items, totals);
            Set<Long> handled = store.cteMatchIdsSince(CTE_SINCE);
            Set<Long> used = store.quotesApprovedByCte();
            List<LegacyCte> ctes = legacy.findRecentCtes(CTE_SINCE).stream()
                    .filter(cte -> !handled.contains(cte.id()))
                    .toList();
            Map<String, List<LegacyQuote>> byPayer = quotes.stream()
                    .filter(quote -> !used.contains(quote.id()))
                    .filter(quote -> quote.payerCnpj() != null && !quote.payerCnpj().isBlank())
                    .collect(Collectors.groupingBy(LegacyQuote::payerCnpj));

            phase("Cruzando " + ctes.size() + " CT-es", items, totals);
            Set<Long> hasCte = new HashSet<>(used);
            LocalDateTime now = LocalDateTime.now(clock);
            for (LegacyCte cte : ctes) {
                var match = CteQuoteMatcher.evaluate(cte, byPayer.getOrDefault(cte.payerCnpj(), List.of()),
                        properties.windowDays());
                if (match.outcome() == CteQuoteMatcher.Outcome.AMBIGUO) {
                    hasCte.addAll(match.tiedQuoteIds());
                    add(items, totals, "AMBIGUO", match.quote(), cte, "não alterada; conferir: " + match.criteria(),
                            "REVISAO");
                    if (execute) {
                        store.recordCteMatch(cte, match.quote(), "AMBIGUO", match.criteria(), null);
                    }
                    continue;
                }
                if (match.outcome() != CteQuoteMatcher.Outcome.APROVAR) continue;
                LegacyQuote quote = match.quote();
                hasCte.add(quote.id());
                byPayer.computeIfPresent(cte.payerCnpj(),
                        (key, list) -> list.stream().filter(q -> q.id() != quote.id()).toList());
                if (!execute) {
                    add(items, totals, "APROVAR", quote, cte, match.criteria(), "SIMULADO");
                    continue;
                }
                try {
                    if (writer.approve(quote, cte, now)) {
                        store.recordCteMatch(cte, quote, "APROVADA_AUTO", match.criteria(), match.divergences());
                        store.recordEvent("cte:" + cte.id() + ":aprovacao", "CTE", cte.id(), "APROVACAO_CTE",
                                "PROCESSADO", "Lote: cotação " + quote.id() + " (" + quote.responsible() + ") "
                                        + quote.status() + " → APROVADA pelo CT-e " + cte.label(), null);
                        store.recordEvent("quote:" + quote.id() + ":lote:aprovada", "COTACAO", quote.id(),
                                "LOTE_APROVADA", "PROCESSADO", "Lote: APROVADA pelo CT-e " + cte.label(), null);
                        add(items, totals, "APROVAR", quote, cte, match.criteria(), "APROVADA");
                    } else {
                        store.recordCteMatch(cte, quote, "CONCORRENCIA", match.criteria(),
                                "Status da cotação mudou antes da gravação");
                        add(items, totals, "APROVAR", quote, cte, "status mudou antes da gravação", "CONCORRENCIA");
                    }
                } catch (Exception exception) {
                    add(items, totals, "APROVAR", quote, cte, exception.getMessage(), "ERRO");
                }
            }

            phase("Marcando cotações sem CT-e", items, totals);
            String description = "Sem CT-e emitido para a cotação (lote Hub CRM " + BR.format(now.toLocalDate()) + ")";
            for (LegacyQuote quote : quotes) {
                if (hasCte.contains(quote.id())) continue;
                if (!"ABERTA".equals(HubCrmNormalization.normalizedText(quote.status()))) continue;
                if (quote.createdDate() == null || !quote.createdDate().isBefore(cutoff)) continue;
                if (!execute) {
                    add(items, totals, "NAO_APROVAR", quote, null, "motivo Preço", "SIMULADO");
                    continue;
                }
                try {
                    if (writer.rejectForPrice(quote, description, now)) {
                        store.recordEvent("quote:" + quote.id() + ":lote:nao-aprovada", "COTACAO", quote.id(),
                                "LOTE_NAO_APROVADA", "PROCESSADO",
                                "Lote: " + quote.status() + " → NÃO APROVADA (Preço), sem CT-e", null);
                        add(items, totals, "NAO_APROVAR", quote, null, "motivo Preço", "NAO_APROVADA");
                    } else {
                        add(items, totals, "NAO_APROVAR", quote, null, "status mudou antes da gravação", "CONCORRENCIA");
                    }
                } catch (Exception exception) {
                    add(items, totals, "NAO_APROVAR", quote, null, exception.getMessage(), "ERRO");
                }
            }
            state.set(state.get().finish(totals, items, null, Instant.now(clock)));
            log.info("Lote do Hub CRM concluído ({}): {}", execute ? "execução" : "simulação", totals);
        } catch (Exception exception) {
            log.error("Falha no lote do Hub CRM", exception);
            state.set(state.get().finish(totals, items, exception.getMessage(), Instant.now(clock)));
        }
    }

    private void phase(String phase, List<BatchItem> items, Map<String, Integer> totals) {
        state.set(state.get().progress(phase, totals, items.size()));
    }

    private void add(List<BatchItem> items, Map<String, Integer> totals, String action, LegacyQuote quote,
            LegacyCte cte, String detail, String result) {
        items.add(new BatchItem(action, result, quote.id(), quote.responsible(), quote.status(), quote.createdDate(),
                cte == null ? null : cte.label(), cte == null ? null : cte.issueDate(), quote.totalFreight(),
                cte == null ? null : cte.totalFreight(), detail));
        totals.merge(action + ":" + result, 1, Integer::sum);
    }

    public record BatchItem(String acao, String resultado, long cotacao, String responsavel, String statusAnterior,
            LocalDate criada, String cte, LocalDate emissao, java.math.BigDecimal freteCotacao,
            java.math.BigDecimal freteCte, String detalhe) {}

    public record BatchState(boolean running, boolean execute, LocalDate cutoff, String phase, Instant startedAt,
            Instant finishedAt, Map<String, Integer> totals, int itemCount, String error, List<BatchItem> items) {
        static BatchState idle() {
            return new BatchState(false, false, null, "Nenhum lote executado", null, null, Map.of(), 0, null, List.of());
        }

        static BatchState started(boolean execute, LocalDate cutoff, Instant at) {
            return new BatchState(true, execute, cutoff, "Iniciando", at, null, Map.of(), 0, null, List.of());
        }

        BatchState progress(String newPhase, Map<String, Integer> newTotals, int count) {
            return new BatchState(true, execute, cutoff, newPhase, startedAt, null, Map.copyOf(newTotals), count, null,
                    List.of());
        }

        BatchState finish(Map<String, Integer> newTotals, List<BatchItem> newItems, String newError, Instant at) {
            return new BatchState(false, execute, cutoff, newError == null ? "Concluído" : "Falhou", startedAt, at,
                    Map.copyOf(newTotals), newItems.size(), newError, List.copyOf(newItems));
        }

        /** Resumo sem a lista (a lista vai pelo endpoint próprio). */
        public Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("running", running);
            result.put("execute", execute);
            result.put("cutoff", cutoff);
            result.put("phase", phase);
            result.put("startedAt", startedAt);
            result.put("finishedAt", finishedAt);
            result.put("totals", totals);
            result.put("itemCount", itemCount);
            result.put("error", error);
            return result;
        }
    }
}
