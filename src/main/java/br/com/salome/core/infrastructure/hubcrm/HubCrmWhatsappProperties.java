package br.com.salome.core.infrastructure.hubcrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envio do PDF da cotação pelo WhatsApp do ArpaSuite.
 *
 * @param fallbackTemplateId template enviado no lugar do PDF quando a janela de 24h da Meta está
 *        fechada (0 = não usa template)
 * @param fallbackTemplateSendsDocument o template já leva a cotação em anexo; sem isso o PDF fica
 *        aguardando o cliente responder ao template
 * @param firstQuoteId primeira cotação que entra no envio (as anteriores não são reenviadas)
 * @param waitDays dias, a partir da data da cotação, esperando a resposta ao template
 */
@ConfigurationProperties(prefix = "salome.hub-crm.whatsapp")
public record HubCrmWhatsappProperties(
        long fallbackTemplateId,
        boolean fallbackTemplateSendsDocument,
        long firstQuoteId,
        int waitDays
) {
    public HubCrmWhatsappProperties {
        if (waitDays <= 0) waitDays = 3;
    }
}
