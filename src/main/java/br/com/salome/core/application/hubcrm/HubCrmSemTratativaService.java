package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.CteQuoteMatcher;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmSemTratativaProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Baixa a proposta sem tratativa do comercial ({@link HubCrmSemTratativaProperties}): cotação da
 * Fernanda/Jaci ABERTA no legado, com card aberto no ArpaSuite, criada há mais de N dias e sem
 * nenhuma atividade no card desde a data da cotação. O legado não tem o motivo "sem tratativa",
 * então vai como NÃO APROVADA por Preço; o evento {@code quote:<id>:sem-tratativa} faz o
 * {@link HubCrmQuoteSyncService} perder o card com o motivo próprio do ArpaSuite.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}"
        + " and ${salome.hub-crm.sem-tratativa.enabled:false}")
public class HubCrmSemTratativaService {
    /** Uma verificação por hora basta: o prazo é em dias e cada card custa duas chamadas ao ArpaSuite. */
    private static final Duration INTERVAL = Duration.ofHours(1);

    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final ArpaSuiteGateway arpa;
    private final LegacyQuoteApprovalWriter writer;
    private final HubCrmSemTratativaProperties properties;
    private final Clock clock;
    private final AtomicReference<Instant> lastRun = new AtomicReference<>();

    @Autowired
    public HubCrmSemTratativaService(HubCrmLegacyRepository legacy, HubCrmStore store, ArpaSuiteGateway arpa,
            LegacyQuoteApprovalWriter writer, HubCrmSemTratativaProperties properties) {
        this(legacy, store, arpa, writer, properties, Clock.system(HubCrmCteApprovalService.LEGACY_ZONE));
    }

    HubCrmSemTratativaService(HubCrmLegacyRepository legacy, HubCrmStore store, ArpaSuiteGateway arpa,
            LegacyQuoteApprovalWriter writer, HubCrmSemTratativaProperties properties, Clock clock) {
        this.legacy = legacy;
        this.store = store;
        this.arpa = arpa;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    /** Chamado a cada polling; roda de fato no máximo uma vez por hora. */
    public synchronized int closeStaleQuotes() {
        Instant now = clock.instant();
        Instant previous = lastRun.get();
        if (previous != null && previous.plus(INTERVAL).isAfter(now)) return 0;
        lastRun.set(now);

        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.minusDays(properties.days());
        Map<Long, Long> deals = store.openQuoteDeals();
        if (deals.isEmpty()) return 0;
        List<LegacyQuote> quotes = legacy.findQuotesByIds(deals.keySet());
        int closed = 0;
        for (LegacyQuote quote : quotes) {
            if (!"ABERTA".equals(HubCrmNormalization.normalizedText(quote.status()))) continue;
            if (!CteQuoteMatcher.inArpaSuite(quote.responsible())) continue;
            if (quote.createdDate() == null || quote.createdDate().isAfter(limit)) continue;
            String eventKey = "quote:" + quote.id() + ":sem-tratativa";
            if (store.eventProcessed(eventKey)) continue;
            long dealId = deals.get(quote.id());
            try {
                if (!"open".equalsIgnoreCase(arpa.findDealStatus(dealId).orElse(""))) continue;
                if (arpa.hasDealActivitySince(dealId, quote.createdDate())) continue;
                if (writer.reject(quote, "naoAprovacaoPreco", properties.description(), LocalDateTime.now(clock))) {
                    closed++;
                    store.recordEvent(eventKey, "COTACAO", quote.id(), "SEM_TRATATIVA", "PROCESSADO",
                            "Cotação " + quote.id() + " (" + quote.responsible() + ") de " + quote.createdDate()
                                    + " sem atividade no card em " + properties.days()
                                    + " dias: NÃO APROVADA no legado (Preço); perdida no ArpaSuite com o motivo "
                                    + properties.lostReasonId(), null);
                }
            } catch (Exception exception) {
                // Isolado por cotação: tenta de novo na próxima hora.
                store.recordEvent("quote:" + quote.id() + ":sem-tratativa-falha", "COTACAO", quote.id(),
                        "SEM_TRATATIVA", "ERRO", null, exception.getMessage());
            }
        }
        return closed;
    }
}
