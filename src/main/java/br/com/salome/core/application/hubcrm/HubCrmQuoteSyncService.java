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

    public QuoteSyncResult syncQuotes() {
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
                trySendPdf(quote, current.peopleId(), current.dealId(), current.whatsappStatus());
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
        var external = storedDeal.isPresent() ? storedDeal : arpa.findLatestOpenDealByCnpj(quote.payerCnpj());
        long organizationId;
        long peopleId;
        long dealId;
        if (external.isPresent() && external.get().organizationId() != null && external.get().peopleId() != null) {
            organizationId = external.get().organizationId();
            peopleId = external.get().peopleId();
            dealId = external.get().id();
        } else {
            organizationId = arpa.createOrganization(quote.payerName());
            peopleId = arpa.createPerson(HubCrmNormalization.shortName(quote.payerName()),
                    quote.payerPhone(), organizationId);
            if (external.isPresent()) {
                dealId = external.get().id();
                arpa.linkDeal(dealId, organizationId, peopleId, userId);
            } else {
                dealId = arpa.createQuoteDeal(quote, organizationId, peopleId, userId);
            }
        }
        arpa.updateOrganization(organizationId, quote.payerName());
        arpa.updatePerson(peopleId, HubCrmNormalization.shortName(quote.payerName()), quote.payerPhone(), organizationId);
        arpa.updateDealFromQuote(dealId, quote, userId);

        String quoteEvent = "quote:" + quote.id() + ":snapshot:" + hash;
        if (!store.eventProcessed(quoteEvent)) {
            long annotationId = arpa.addAnnotation(dealId, quoteAnnotation(quote));
            store.recordEvent(quoteEvent, "COTACAO", quote.id(), "COTACAO_SALVA",
                    "PROCESSADO", "Anotação " + annotationId, null);
        }
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

    private void applyStatus(LegacyQuote quote, long dealId) {
        String status = HubCrmNormalization.normalizedText(quote.status());
        String eventKey = "quote:" + quote.id() + ":status:" + status + ":" + quote.statusAt();
        if (store.eventProcessed(eventKey)) return;
        if ("APROVADA".equals(status)) {
            arpa.markWon(dealId, quote.statusAt());
            arpa.addAnnotation(dealId, "Cotação " + quote.id() + " aprovada no legado em " + quote.statusAt());
            store.recordEvent(eventKey, "COTACAO", quote.id(), "GANHO", "PROCESSADO", "Card ganho", null);
        } else if ("NAO APROVADA".equals(status)) {
            if (quote.selectedLossReasons().size() != 1) {
                throw new ReviewException("Cotação não aprovada deve possuir exatamente um dos 10 motivos ativos; encontrados: "
                        + quote.selectedLossReasons().size());
            }
            Map.Entry<LossReason, String> reason = quote.selectedLossReasons().entrySet().iterator().next();
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
                safe(q.senderName()), safe(q.senderCnpj()), safe(q.recipientName()), safe(q.recipientCnpj()),
                safe(q.cargoType()), q.volumes(), decimal(q.weight()), decimal(q.cubage()), money(q.invoiceValue()),
                money(q.freightWeight()), money(q.freightValue()), money(q.toll()), money(q.pickup()),
                money(q.delivery()), money(q.dispatch()), money(q.gris()), money(q.redelivery()), money(q.icms()),
                money(q.discount()), money(q.addition()), money(q.totalFreight()));
    }

    private String quoteHash(LegacyQuote quote) {
        return HubCrmNormalization.sha256(quote.id(), quote.status(), quote.statusAt(), quote.responsible(),
                quote.paymentType(), quote.senderCnpj(), quote.senderName(), quote.senderCity(),
                quote.recipientCnpj(), quote.recipientName(), quote.recipientCity(), quote.payerCnpj(),
                quote.payerName(), quote.payerPhone(), quote.payerEmail(), quote.cargoType(), quote.volumes(),
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

    private static final class ReviewException extends RuntimeException {
        private ReviewException(String message) { super(message); }
    }
}
