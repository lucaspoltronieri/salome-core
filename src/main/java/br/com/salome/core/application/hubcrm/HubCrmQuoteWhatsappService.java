package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.hubcrm.HubCrmWhatsappProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * PDF da cotação pelo WhatsApp — regra do Lucas (17/09/2026): só vai quando já existe conversa
 * aberta (janela de 24h) com o cliente, e nunca por template. A conversa pode ser da pessoa do
 * card, do mesmo telefone, de uma pessoa da mesma organização ou com o mesmo nome da empresa
 * (cliente falando de outro número). Sem conversa, o Hub volta a procurar a cada ciclo até
 * {@code waitDays} depois da data da cotação e então encerra sem enviar.
 * O estado fica nos eventos (enviado / encerrado), então nada é reenviado.
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

    public void process(LegacyQuote quote, Long organizationId, Long peopleId, Long dealId) {
        if (peopleId == null || dealId == null || quote.id() < properties.firstQuoteId()) return;
        String sentKey = "quote:" + quote.id() + ":whatsapp";
        String closedKey = "quote:" + quote.id() + ":whatsapp-encerrado";
        if (store.eventProcessed(sentKey) || store.eventProcessed(closedKey)) return;
        if (!arpa.hasWhatsappChannel()) {
            store.markWhatsapp(quote.id(), "AGUARDANDO_CANAL", null);
            return;
        }
        if (LocalDate.now(clock).isAfter(quote.createdDate().plusDays(properties.waitDays()))) {
            store.markWhatsapp(quote.id(), "SEM_CONVERSA", null);
            store.recordEvent(closedKey, "COTACAO", quote.id(), "WHATSAPP_SEM_CONVERSA", "PROCESSADO",
                    "Nenhuma conversa aberta em " + properties.waitDays() + " dias; PDF não enviado", null);
            return;
        }
        try {
            Optional<ArpaSuiteGateway.OpenConversation> conversation = arpa.findOpenConversation(peopleId,
                    quote.payerPhone(), organizationId, HubCrmNormalization.businessName(quote.payerName()),
                    HubCrmNormalization.shortName(quote.payerName()));
            if (conversation.isEmpty()) {
                store.markWhatsapp(quote.id(), "AGUARDANDO_CONVERSA", null);
                return;
            }
            arpa.sendDocumentToConversation(conversation.get().id(), dealId, mediaSigner.quoteUrl(quote.id()).url(),
                    "Cotação de frete nº " + quote.id());
            store.markWhatsapp(quote.id(), "ENVIADO", null);
            store.recordEvent(sentKey, "COTACAO", quote.id(), "WHATSAPP_PDF", "PROCESSADO",
                    "PDF enviado na conversa " + conversation.get().id() + " (" + conversation.get().match() + ")",
                    null);
        } catch (RestClientResponseException exception) {
            String body = exception.getResponseBodyAsString();
            if (body.contains("window_closed")) {
                // A janela fechou entre a consulta e o envio: procura de novo no próximo ciclo.
                return;
            }
            if (exception.getStatusCode().is4xxClientError() && exception.getStatusCode().value() != 429) {
                // Erro de validação não se resolve sozinho: encerra em vez de repetir a cada ciclo.
                store.markWhatsapp(quote.id(), "ERRO", exception.getMessage());
                store.recordEvent(closedKey, "COTACAO", quote.id(), "WHATSAPP_ERRO", "PROCESSADO",
                        exception.getMessage(), null);
                return;
            }
            failed(quote, exception);
        } catch (Exception exception) {
            failed(quote, exception);
        }
    }

    private void failed(LegacyQuote quote, Exception exception) {
        store.markWhatsapp(quote.id(), "ERRO", exception.getMessage());
        store.recordEvent("quote:" + quote.id() + ":whatsapp-falha", "COTACAO", quote.id(), "WHATSAPP_PDF",
                "ERRO", null, exception.getMessage());
    }
}
