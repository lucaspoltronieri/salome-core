package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import br.com.salome.core.domain.hubcrm.QuoteIntegration;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmQuoteSyncService {
    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final ArpaSuiteGateway arpa;
    private final HubCrmProperties properties;
    private final HubCrmMediaSigner mediaSigner;

    public HubCrmQuoteSyncService(HubCrmLegacyRepository legacy, HubCrmStore store,
            ArpaSuiteGateway arpa, HubCrmProperties properties, HubCrmMediaSigner mediaSigner) {
        this.legacy = legacy;
        this.store = store;
        this.arpa = arpa;
        this.properties = properties;
        this.mediaSigner = mediaSigner;
    }

    public synchronized QuoteSyncResult syncQuotes() {
        long checkpoint = store.checkpoint("last_quote_id", 0);
        Map<Long, LegacyQuote> quotes = new LinkedHashMap<>();
        legacy.findQuotesAfter(checkpoint).forEach(quote -> quotes.put(quote.id(), quote));
        legacy.findQuotesByIds(store.trackedQuoteIds()).forEach(quote -> quotes.put(quote.id(), quote));
        int integrated = 0;
        int skipped = 0;
        int review = 0;
        int failed = 0;
        long highest = checkpoint;
        for (LegacyQuote quote : quotes.values()) {
            highest = Math.max(highest, quote.id());
            String hash = quoteHash(quote);
            store.discoverQuote(quote, hash);
            var current = store.findQuote(quote.id()).orElseThrow();
            if ("INTEGRADO".equals(current.status()) && hash.equals(current.snapshotHash())) {
                // Reprocessa somente transições que ainda não possuem evento concluído.
                // Nunca envia novamente um ganho/perda confirmado a cada polling.
                // A falha aqui fica isolada nesta cotação: sem o try/catch, um erro no
                // reprocesso de uma cotação antiga abortava o polling inteiro antes do
                // checkpoint e nenhuma cotação nova subia (incidente de 09/2026).
                try {
                    if (current.dealId() != null) {
                        applyQuoteAnnotation(quote, current.dealId());
                        applyStatus(quote, current.dealId());
                    }
                    trySendPdf(quote, current.peopleId(), current.dealId(), current.whatsappStatus());
                    skipped++;
                } catch (Exception exception) {
                    // Não muda o status da cotação: ela continua INTEGRADO e o
                    // reprocesso é tentado de novo no próximo polling.
                    failed++;
                    store.recordEvent("quote:" + quote.id() + ":reprocesso:" + hash, "COTACAO",
                            quote.id(), "REPROCESSAR", "ERRO", null, exception.getMessage());
                }
                continue;
            }
            if ("REMOVIDO".equals(current.status())) {
                // Card apagado no ArpaSuite: não recria e não fica tentando.
                skipped++;
                continue;
            }
            if ("REVISAO".equals(current.status())
                    || ("ERRO".equals(current.status()) && !store.canRetryQuote(quote.id()))) {
                skipped++;
                continue;
            }
            try {
                if (quote.payerCnpj().length() != 14) {
                    throw new ReviewException("CNPJ do pagador inválido: " + quote.payerCnpj());
                }
                integrate(quote, hash, current);
                integrated++;
            } catch (DealRemovedException exception) {
                skipped++;
                store.markQuoteRemoved(quote.id());
                store.recordEvent("quote:" + quote.id() + ":removido", "COTACAO", quote.id(),
                        "CARD_REMOVIDO", "PROCESSADO", "Card apagado no ArpaSuite; não recriado", null);
            } catch (ReviewException exception) {
                review++;
                store.markQuoteReview(quote.id(), exception.getMessage());
                store.recordEvent("quote:" + quote.id() + ":review:" + hash, "COTACAO", quote.id(),
                        "REVISAO", "REVISAO", null, exception.getMessage());
            } catch (Exception exception) {
                failed++;
                store.markQuoteError(quote.id(), exception);
                store.recordEvent("quote:" + quote.id() + ":" + hash, "COTACAO", quote.id(),
                        "SINCRONIZAR", "ERRO", null, exception.getMessage());
            }
        }
        store.setCheckpoint("last_quote_id", highest);
        return new QuoteSyncResult(integrated, skipped, review, failed);
    }

    private void integrate(LegacyQuote quote, String hash, QuoteIntegration current) {
        long userId = owner(quote.responsible());
        var storedDeal = current.dealId() == null ? java.util.Optional.<ArpaSuiteGateway.ArpaDeal>empty()
                : arpa.findDeal(current.dealId());
        // Card que já existiu e sumiu foi apagado no ArpaSuite: registra a remoção em vez
        // de criar outro. Só cotação que nunca teve card entra no fluxo de criação.
        if (current.dealId() != null && storedDeal.isEmpty()) throw new DealRemovedException();
        var quoteDeal = storedDeal.isPresent() ? storedDeal : arpa.findDealByLegacyQuoteId(quote.id());
        var openDeal = quoteDeal.isPresent() ? java.util.Optional.<ArpaSuiteGateway.ArpaDeal>empty()
                : arpa.findLatestOpenDealByCnpj(quote.payerCnpj())
                        .filter(deal -> !store.dealBoundToOtherQuote(deal.id(), quote.id()));
        var external = quoteDeal.isPresent() ? quoteDeal : openDeal;
        long organizationId;
        long peopleId;
        long dealId;
        if (external.isPresent() && external.get().organizationId() != null && external.get().peopleId() != null) {
            organizationId = external.get().organizationId();
            peopleId = external.get().peopleId();
            dealId = external.get().id();
        } else {
            organizationId = arpa.createOrganization(HubCrmNormalization.businessName(quote.payerName()));
            peopleId = arpa.createPerson(HubCrmNormalization.shortName(quote.payerName()),
                    quote.payerPhone(), organizationId);
            if (external.isPresent()) {
                dealId = external.get().id();
                arpa.linkDeal(dealId, organizationId, peopleId, userId);
            } else {
                dealId = arpa.createQuoteDeal(quote, organizationId, peopleId, userId);
            }
        }
        // O vínculo é persistido antes das atualizações e da timeline. Assim, uma
        // falha posterior nunca transforma a mesma cotação em um novo card no retry.
        store.bindQuote(quote.id(), organizationId, peopleId, dealId, userId);
        arpa.updateOrganization(organizationId, HubCrmNormalization.businessName(quote.payerName()));
        arpa.updatePerson(peopleId, HubCrmNormalization.shortName(quote.payerName()), quote.payerPhone(), organizationId);
        arpa.updateDealFromQuote(dealId, quote, userId);

        applyQuoteAnnotation(quote, dealId);
        applyStatus(quote, dealId);
        store.markQuoteIntegrated(quote, organizationId, peopleId, dealId, userId, hash,
                arpa.hasWhatsappChannel() ? "PENDENTE" : "AGUARDANDO_CANAL");
        trySendPdf(quote, peopleId, dealId,
                arpa.hasWhatsappChannel() ? "PENDENTE" : "AGUARDANDO_CANAL");
    }

    private void trySendPdf(LegacyQuote quote, Long peopleId, Long dealId, String currentStatus) {
        if (peopleId == null || dealId == null || "ENVIADO".equals(currentStatus)) return;
        if (!arpa.hasWhatsappChannel()) {
            store.markWhatsapp(quote.id(), "AGUARDANDO_CANAL", null);
            return;
        }
        String eventKey = "quote:" + quote.id() + ":whatsapp";
        if (store.eventProcessed(eventKey)) {
            store.markWhatsapp(quote.id(), "ENVIADO", null);
            return;
        }
        try {
            String mediaUrl = mediaSigner.quoteUrl(quote.id()).url();
            arpa.sendQuoteDocument(peopleId, dealId, mediaUrl, "Cotação de frete nº " + quote.id());
            store.markWhatsapp(quote.id(), "ENVIADO", null);
            store.recordEvent(eventKey, "COTACAO", quote.id(), "WHATSAPP_PDF",
                    "PROCESSADO", "Documento enviado", null);
        } catch (Exception exception) {
            store.markWhatsapp(quote.id(), "ERRO", exception.getMessage());
            store.recordEvent(eventKey, "COTACAO", quote.id(), "WHATSAPP_PDF",
                    "ERRO", null, exception.getMessage());
        }
    }

    void applyStatus(LegacyQuote quote, long dealId) {
        String status = HubCrmNormalization.normalizedText(quote.status());
        String eventKey = "quote:" + quote.id() + ":status:" + status + ":" + quote.statusAt();
        if ("APROVADA".equals(status)) {
            if (store.eventProcessed(eventKey)) return;
            arpa.markWon(dealId, quote.statusAt());
            String viaCte = store.cteApprovalNote(quote.id())
                    .map(note -> " automaticamente pelo " + note).orElse("");
            arpa.addAnnotation(dealId, "Cotação " + quote.id() + " aprovada no legado em " + quote.statusAt() + viaCte);
            store.recordEvent(eventKey, "COTACAO", quote.id(), "GANHO", "PROCESSADO", "Card ganho", null);
        } else if ("NAO APROVADA".equals(status)) {
            if (quote.selectedLossReasons().size() != 1) {
                throw new ReviewException("Cotação não aprovada deve possuir exatamente um dos 10 motivos ativos; encontrados: "
                        + quote.selectedLossReasons().size());
            }
            Map.Entry<LossReason, String> reason = quote.selectedLossReasons().entrySet().iterator().next();
            if (store.eventProcessed(eventKey)) return;
            arpa.markLost(dealId, reason.getKey(), quote.statusAt());
            arpa.addAnnotation(dealId, "Cotação " + quote.id() + " não aprovada. Motivo: "
                    + reason.getKey().arpaName() + ". Observação: " + safe(reason.getValue()));
            store.recordEvent(eventKey, "COTACAO", quote.id(), "PERDIDO", "PROCESSADO",
                    reason.getKey().arpaName(), null);
        }
    }

    private String quoteAnnotation(LegacyQuote q) {
        return """
                COTAÇÃO ID %d
                Responsável: %s
                Pagamento: %s
                Remetente: %s (%s)
                Destinatário: %s (%s)
                Carga: %s | Volumes: %d | Peso: %s kg | Cubagem: %s m³
                Valor NF: %s
                Frete peso: %s | Frete valor: %s | Pedágio: %s | Coleta: %s
                Entrega: %s | Despacho: %s | GRIS: %s | Redespacho: %s | ICMS: %s
                Desconto: %s | Acréscimo: %s | Total: %s
                """.formatted(q.id(), safe(q.responsible()), safe(q.paymentType()),
                safe(HubCrmNormalization.businessName(q.senderName())), safe(q.senderCnpj()),
                safe(HubCrmNormalization.businessName(q.recipientName())), safe(q.recipientCnpj()),
                safe(q.cargoType()), q.volumes(), decimal(q.weight()), decimal(q.cubage()), money(q.invoiceValue()),
                money(q.freightWeight()), money(q.freightValue()), money(q.toll()), money(q.pickup()),
                money(q.delivery()), money(q.dispatch()), money(q.gris()), money(q.redelivery()), money(q.icms()),
                money(q.discount()), money(q.addition()), money(q.totalFreight()));
    }

    void applyQuoteAnnotation(LegacyQuote quote, long dealId) {
        if (!hasCalculatedFreight(quote)) return;
        String annotation = quoteAnnotation(quote);
        String eventKey = "quote:" + quote.id() + ":content:" + HubCrmNormalization.sha256(annotation);
        if (store.eventProcessed(eventKey)) return;

        // Compatibilidade dos registros criados antes do hash de conteúdo: no
        // primeiro ciclo, memoriza a observação já existente sem publicá-la de novo.
        if (!store.hasProcessedQuoteContentEvent(quote.id())
                && store.hasProcessedLegacyQuoteSnapshotEvent(quote.id())) {
            store.recordEvent(eventKey, "COTACAO", quote.id(), "COTACAO_SALVA",
                    "PROCESSADO", "Conteúdo existente indexado sem nova anotação", null);
            return;
        }

        long annotationId = arpa.addAnnotation(dealId, annotation);
        store.recordEvent(eventKey, "COTACAO", quote.id(), "COTACAO_SALVA",
                "PROCESSADO", "Anotação " + annotationId, null);
    }

    private boolean hasCalculatedFreight(LegacyQuote quote) {
        return quote.totalFreight() != null && quote.totalFreight().compareTo(BigDecimal.ZERO) > 0;
    }

    private String quoteHash(LegacyQuote quote) {
        return HubCrmNormalization.sha256(quote.id(), quote.status(), quote.statusAt(), quote.responsible(),
                quote.paymentType(), quote.senderCnpj(), HubCrmNormalization.businessName(quote.senderName()),
                quote.senderCity(), quote.recipientCnpj(), HubCrmNormalization.businessName(quote.recipientName()),
                quote.recipientCity(), quote.payerCnpj(), HubCrmNormalization.businessName(quote.payerName()),
                quote.payerPhone(), quote.payerEmail(), quote.cargoType(), quote.volumes(),
                quote.weight(), quote.invoiceValue(), quote.cubage(), quote.freightWeight(), quote.freightValue(),
                quote.toll(), quote.pickup(), quote.delivery(), quote.dispatch(), quote.gris(), quote.redelivery(),
                quote.icms(), quote.discount(), quote.addition(), quote.totalFreight(), quote.selectedLossReasons());
    }

    private long owner(String responsible) {
        String normalized = HubCrmNormalization.normalizedText(responsible);
        return normalized.contains("FERNANDA") ? properties.arpa().fernandaUserId()
                : properties.arpa().jaciUserId();
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "não informado" : value.trim();
    }

    public record QuoteSyncResult(int integrated, int skipped, int review, int failed) {}

    private static final class DealRemovedException extends RuntimeException {
        private DealRemovedException() { super("Card apagado no ArpaSuite"); }
    }

    private static final class ReviewException extends RuntimeException {
        private ReviewException(String message) { super(message); }
    }
}
