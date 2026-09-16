package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.hubcrm.HubCrmWhatsappProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * PDF da cotação pelo WhatsApp (regra do Lucas, 16/09/2026):
 * <ol>
 *   <li>janela de 24h aberta com o contato (conversa ativa no conversacional): vai o PDF direto;</li>
 *   <li>janela fechada: a API envia o template no lugar ({@code fallbackTemplateId}) e a cotação
 *       fica aguardando; quando o cliente responde e a janela abre, o Hub manda o PDF;</li>
 *   <li>sem resposta até {@code waitDays} depois da data da cotação: encerra sem enviar.</li>
 * </ol>
 * O estado fica nos eventos (enviado / template / encerrado), então nada é reenviado quando a
 * cotação muda no legado. Cotações anteriores a {@code firstQuoteId} não entram.
 */
@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmQuoteWhatsappService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final HubCrmStore store;
    private final ArpaSuiteGateway arpa;
    private final HubCrmMediaSigner mediaSigner;
    private final HubCrmWhatsappProperties properties;
    private final Clock clock;

    @Autowired
    public HubCrmQuoteWhatsappService(HubCrmStore store, ArpaSuiteGateway arpa, HubCrmMediaSigner mediaSigner,
            HubCrmWhatsappProperties properties) {
        this(store, arpa, mediaSigner, properties, Clock.system(ZONE));
    }

    HubCrmQuoteWhatsappService(HubCrmStore store, ArpaSuiteGateway arpa, HubCrmMediaSigner mediaSigner,
            HubCrmWhatsappProperties properties, Clock clock) {
        this.store = store;
        this.arpa = arpa;
        this.mediaSigner = mediaSigner;
        this.properties = properties;
        this.clock = clock;
    }

    public void process(LegacyQuote quote, Long peopleId, Long dealId) {
        if (peopleId == null || dealId == null || quote.id() < properties.firstQuoteId()) return;
        String sentKey = "quote:" + quote.id() + ":whatsapp";
        String templateKey = "quote:" + quote.id() + ":whatsapp-template";
        String closedKey = "quote:" + quote.id() + ":whatsapp-encerrado";
        if (store.eventProcessed(sentKey) || store.eventProcessed(closedKey)) return;
        if (!arpa.hasWhatsappChannel()) {
            store.markWhatsapp(quote.id(), "AGUARDANDO_CANAL", null);
            return;
        }
        boolean waiting = store.eventProcessed(templateKey);
        try {
            if (waiting) {
                LocalDate limit = quote.createdDate().plusDays(properties.waitDays());
                if (LocalDate.now(clock).isAfter(limit)) {
                    close(quote, closedKey, "SEM_RESPOSTA", "Cliente não respondeu ao template em "
                            + properties.waitDays() + " dias; PDF não enviado");
                    return;
                }
                if (!arpa.whatsappWindowOpen(peopleId)) return;
                arpa.sendQuoteDocument(peopleId, dealId, pdfUrl(quote), caption(quote), null);
                sent(quote, sentKey, "PDF enviado depois da resposta do cliente ao template");
                return;
            }
            Long fallback = properties.fallbackTemplateId() > 0 ? properties.fallbackTemplateId() : null;
            String dispatch = arpa.sendQuoteDocument(peopleId, dealId, pdfUrl(quote), caption(quote), fallback);
            if (!"fallback_template".equals(dispatch)) {
                sent(quote, sentKey, "PDF enviado (janela de 24h aberta)");
            } else if (properties.fallbackTemplateSendsDocument()) {
                sent(quote, sentKey, "Janela fechada: enviado o template " + fallback + " com a cotação");
            } else {
                store.markWhatsapp(quote.id(), "AGUARDANDO_RESPOSTA", null);
                store.recordEvent(templateKey, "COTACAO", quote.id(), "WHATSAPP_TEMPLATE", "PROCESSADO",
                        "Janela fechada: enviado o template " + fallback + "; PDF aguarda a resposta", null);
            }
        } catch (RestClientResponseException exception) {
            String body = exception.getResponseBodyAsString();
            if (body.contains("people_without_phone")) {
                close(quote, closedKey, "SEM_TELEFONE", "Pessoa sem telefone no ArpaSuite");
            } else if (waiting && body.contains("window_closed")) {
                // A janela fechou entre a consulta e o envio: tenta de novo no próximo ciclo.
                return;
            } else if (exception.getStatusCode().is4xxClientError() && exception.getStatusCode().value() != 429) {
                // Erro de validação não se resolve sozinho: encerra em vez de repetir a cada ciclo.
                close(quote, closedKey, "ERRO", exception.getMessage());
            } else {
                failed(quote, exception);
            }
        } catch (Exception exception) {
            failed(quote, exception);
        }
    }

    private String pdfUrl(LegacyQuote quote) {
        return mediaSigner.quoteUrl(quote.id()).url();
    }

    private static String caption(LegacyQuote quote) {
        return "Cotação de frete nº " + quote.id();
    }

    private void sent(LegacyQuote quote, String key, String summary) {
        store.markWhatsapp(quote.id(), "ENVIADO", null);
        store.recordEvent(key, "COTACAO", quote.id(), "WHATSAPP_PDF", "PROCESSADO", summary, null);
    }

    private void close(LegacyQuote quote, String key, String status, String reason) {
        store.markWhatsapp(quote.id(), status, "SEM_RESPOSTA".equals(status) ? null : reason);
        store.recordEvent(key, "COTACAO", quote.id(), "WHATSAPP_" + status, "PROCESSADO", reason, null);
    }

    private void failed(LegacyQuote quote, Exception exception) {
        store.markWhatsapp(quote.id(), "ERRO", exception.getMessage());
        store.recordEvent("quote:" + quote.id() + ":whatsapp-falha", "COTACAO", quote.id(), "WHATSAPP_PDF",
                "ERRO", null, exception.getMessage());
    }
}
