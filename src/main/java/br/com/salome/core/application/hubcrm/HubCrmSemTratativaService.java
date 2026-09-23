package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import br.com.salome.core.infrastructure.hubcrm.HubCrmSemTratativaProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Baixa a proposta parada há mais de N dias ({@link HubCrmSemTratativaProperties}). Pega toda
 * cotação ABERTA no legado, com ou sem card no ArpaSuite (regra do Lucas, 23/09/2026 — as 33 que
 * se acumularam em 09/2026 eram justamente as que a primeira versão não via):
 *
 * <ul>
 *   <li><b>card aberto:</b> só baixa se não houver nenhuma atividade no card desde a data da
 *       cotação (a atividade é o sinal de "falei com o cliente");</li>
 *   <li><b>card já perdido:</b> o legado recebe o mesmo motivo do card, para não sobrescrever no
 *       ArpaSuite o que o comercial escolheu;</li>
 *   <li><b>card apagado, cotação sem card ou responsável fora do ArpaSuite (Carlos):</b> baixa
 *       direto no legado;</li>
 *   <li><b>card ganho:</b> não baixa; fica registrado em REVISAO para conferência manual, porque
 *       o certo nesse caso costuma ser aprovar.</li>
 * </ul>
 *
 * No legado o motivo é Preço com a descrição da regra (o legado não tem "sem tratativa"); o evento
 * {@code quote:<id>:sem-tratativa} faz o {@link HubCrmQuoteSyncService} perder o card com o motivo
 * próprio do ArpaSuite. Aparecendo um CT-e depois, a cotação é reaprovada pelo
 * {@link HubCrmCteApprovalService}, que também olha as NÃO APROVADAS.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}"
        + " and ${salome.hub-crm.sem-tratativa.enabled:false}")
public class HubCrmSemTratativaService {
    /** Uma verificação por hora basta: o prazo é em dias e cada card custa chamadas ao ArpaSuite. */
    private static final Duration INTERVAL = Duration.ofHours(1);
    /** Quanto tempo atrás ainda vale procurar cotação parada (além do prazo da regra). */
    private static final int EXTRA_DAYS = 120;

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

        LocalDate limit = LocalDate.now(clock).minusDays(properties.days());
        List<LegacyQuote> quotes = legacy.findApprovableQuotes(limit.minusDays(EXTRA_DAYS)).stream()
                .filter(quote -> "ABERTA".equals(HubCrmNormalization.normalizedText(quote.status())))
                .filter(quote -> quote.createdDate() != null && !quote.createdDate().isAfter(limit))
                .toList();
        int closed = 0;
        for (LegacyQuote quote : quotes) {
            String eventKey = "quote:" + quote.id() + ":sem-tratativa";
            if (store.eventProcessed(eventKey)) continue;
            try {
                if (close(quote, eventKey)) closed++;
            } catch (Exception exception) {
                // Isolado por cotação: tenta de novo na próxima hora.
                store.recordEvent("quote:" + quote.id() + ":sem-tratativa-falha", "COTACAO", quote.id(),
                        "SEM_TRATATIVA", "ERRO", null, exception.getMessage());
            }
        }
        return closed;
    }

    private boolean close(LegacyQuote quote, String eventKey) {
        Long dealId = store.findQuote(quote.id()).map(q -> q.dealId()).orElse(null);
        String column = "naoAprovacaoPreco";
        String description = properties.description();
        String arpaNote = "perdida no ArpaSuite com o motivo " + properties.lostReasonId();
        boolean semTratativa = true;

        if (dealId != null) {
            String status = arpa.findDealStatus(dealId).orElse("");
            switch (status) {
                case "open" -> {
                    if (arpa.hasDealActivitySince(dealId, quote.createdDate())) return false;
                }
                case "won" -> {
                    store.recordEvent("quote:" + quote.id() + ":sem-tratativa-revisao", "COTACAO", quote.id(),
                            "SEM_TRATATIVA", "REVISAO", null, "Cotação ABERTA no legado há mais de "
                                    + properties.days() + " dias, mas o card " + dealId
                                    + " está ganho no ArpaSuite; conferir à mão");
                    return false;
                }
                case "lost" -> {
                    // Já perdida no ArpaSuite: o legado recebe o mesmo motivo e o card fica como está.
                    Optional<LossReason> reason = arpa.findDealLostReason(dealId);
                    if (reason.isPresent()) {
                        column = reason.get().legacyColumn();
                        description = "Card perdido no ArpaSuite (" + reason.get().arpaName()
                                + "); cotação baixada no legado pelo Hub";
                        arpaNote = "card já estava perdido (" + reason.get().arpaName() + ")";
                        semTratativa = false;
                    }
                }
                default -> { }  // card apagado: só o legado.
            }
        }

        if (!writer.reject(quote, column, description, LocalDateTime.now(clock))) return false;
        if (semTratativa) {
            store.recordEvent(eventKey, "COTACAO", quote.id(), "SEM_TRATATIVA", "PROCESSADO",
                    resume(quote, dealId) + "; " + arpaNote, null);
        } else {
            store.recordEvent(eventKey, "COTACAO", quote.id(), "SEM_TRATATIVA_CARD_PERDIDO", "PROCESSADO",
                    resume(quote, dealId) + "; " + arpaNote, null);
        }
        return true;
    }

    private String resume(LegacyQuote quote, Long dealId) {
        return "Cotação " + quote.id() + " (" + quote.responsible() + ") de " + quote.createdDate()
                + " parada há mais de " + properties.days() + " dias"
                + (dealId == null ? ", sem card no ArpaSuite" : ", card " + dealId)
                + ": NÃO APROVADA no legado";
    }
}
