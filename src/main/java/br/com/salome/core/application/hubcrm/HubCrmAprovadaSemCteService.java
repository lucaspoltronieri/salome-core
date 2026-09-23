package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyColeta;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuoteChain;
import br.com.salome.core.infrastructure.hubcrm.HubCrmPosAprovacaoProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Triagem da cotação aprovada que não virou CT-e (decisão do Lucas, 23/09/2026): passados
 * {@code days} dias da aprovação, sem CT-e amarrado e sem coleta em andamento, a cotação volta a
 * NÃO APROVADA no legado com o motivo <b>Arrependimento do frete</b>.
 *
 * <p>Vale para qualquer responsável. Quem tem card no ArpaSuite perde o card no ciclo seguinte pelo
 * caminho normal do {@link HubCrmQuoteSyncService}: "Arrependimento do frete" é um dos dez motivos
 * do catálogo, então não precisa de evento nem de motivo próprio.
 *
 * <p>Só alcança cotações aprovadas a partir da data de ativação — o histórico antigo fica como está.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}"
        + " and ${salome.hub-crm.pos-aprovacao.triagem-enabled:false}")
public class HubCrmAprovadaSemCteService {
    /** Coluna do motivo na tela de não aprovação do legado. */
    static final String REASON_COLUMN = "naoAprovacaoArrependimentoFrete";
    /** O prazo é em dias; uma verificação por hora basta. */
    private static final Duration INTERVAL = Duration.ofHours(1);

    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final LegacyQuoteApprovalWriter writer;
    private final HubCrmPosAprovacaoProperties properties;
    private final Clock clock;
    private final AtomicReference<Instant> lastRun = new AtomicReference<>();

    @Autowired
    public HubCrmAprovadaSemCteService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmPosAprovacaoProperties properties) {
        this(legacy, store, writer, properties, Clock.system(HubCrmCteApprovalService.LEGACY_ZONE));
    }

    HubCrmAprovadaSemCteService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmPosAprovacaoProperties properties, Clock clock) {
        this.legacy = legacy;
        this.store = store;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    /** Chamado a cada polling; roda de fato no máximo uma vez por hora. */
    public synchronized int rejectApprovedWithoutCte() {
        Instant now = clock.instant();
        Instant previous = lastRun.get();
        if (previous != null && previous.plus(INTERVAL).isAfter(now)) return 0;
        lastRun.set(now);

        LocalDate today = LocalDate.now(clock);
        LocalDate activation = activationDate(today);
        LocalDate limit = today.minusDays(properties.days());
        if (activation.isAfter(limit)) return 0;

        List<LegacyQuote> quotes = legacy.findApprovedQuotesSince(activation).stream()
                .filter(quote -> "APROVADA".equals(HubCrmNormalization.normalizedText(quote.status())))
                .filter(quote -> quote.statusAt() != null && !quote.statusAt().toLocalDate().isAfter(limit))
                .toList();
        if (quotes.isEmpty()) return 0;

        Set<Long> bound = store.quotesApprovedByCte();
        Set<Long> review = store.quotesInReview();
        Map<Long, List<LegacyQuoteChain>> chains = legacy.findQuoteChains(
                quotes.stream().map(LegacyQuote::id).toList()).stream()
                .collect(Collectors.groupingBy(LegacyQuoteChain::quoteId, HashMap::new, Collectors.toList()));

        int rejected = 0;
        for (LegacyQuote quote : quotes) {
            if (bound.contains(quote.id()) || review.contains(quote.id())) continue;
            // Coleta em andamento segura o prazo (decisão do Lucas); coleta já retornada com CT-e
            // vira amarração no próximo ciclo, então também não reprova.
            if (holdsTriage(chains.getOrDefault(quote.id(), List.of()))) continue;
            String eventKey = "quote:" + quote.id() + ":aprovada-sem-cte";
            if (store.eventProcessed(eventKey)) continue;
            try {
                String description = properties.description();
                if (writer.reject(quote, REASON_COLUMN, description, LocalDateTime.now(clock))) {
                    rejected++;
                    String summary = "Cotação " + quote.id() + " (" + quote.responsible() + ") aprovada em "
                            + quote.statusAt().toLocalDate() + " sem CT-e em " + properties.days()
                            + " dias: APROVADA → NÃO APROVADA no legado (Arrependimento do frete)";
                    store.recordEvent(eventKey, "COTACAO", quote.id(), "APROVADA_SEM_CTE", "PROCESSADO",
                            summary, null);
                    store.markQuoteApprovalStatus(quote.id(), "REPROVADA_SEM_CTE", description);
                } else {
                    // Alguém mexeu na cotação entre a leitura e a gravação: nada foi alterado.
                    store.recordEvent(eventKey + "-falha", "COTACAO", quote.id(), "APROVADA_SEM_CTE", "REVISAO",
                            null, "Status da cotação mudou antes da gravação");
                }
            } catch (Exception exception) {
                // Isolado por cotação: tenta de novo na próxima hora.
                store.recordEvent(eventKey + "-falha", "COTACAO", quote.id(), "APROVADA_SEM_CTE", "ERRO", null,
                        exception.getMessage());
            }
        }
        return rejected;
    }

    /**
     * Data a partir da qual a triagem vale. Fica gravada no checkpoint na primeira execução, para
     * ser a data em que a regra foi <b>ligada</b> — e não a de um deploy ou de um restart.
     */
    LocalDate activationDate(LocalDate today) {
        return store.textCheckpoint(HubCrmPosAprovacaoProperties.ACTIVATION_CHECKPOINT)
                .map(LocalDate::parse)
                .orElseGet(() -> {
                    LocalDate date = properties.activationDate() == null ? today : properties.activationDate();
                    store.setTextCheckpoint(HubCrmPosAprovacaoProperties.ACTIVATION_CHECKPOINT, date.toString());
                    return date;
                });
    }

    /** A cotação ainda está em curso: coleta em andamento ou coleta que já gerou CT-e. */
    static boolean holdsTriage(List<LegacyQuoteChain> chains) {
        return chains.stream().anyMatch(chain -> chain.aliveColeta() || chain.cteId() != null);
    }
}
