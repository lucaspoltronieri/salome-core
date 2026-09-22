package br.com.salome.core.infrastructure.hubcrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envio do PDF da cotação pelo WhatsApp do ArpaSuite (só em conversa já aberta, sem template).
 *
 * @param firstQuoteId primeira cotação que entra no envio (as anteriores não são reenviadas)
 * @param waitDays dias, a partir da data da cotação, procurando uma conversa aberta
 */
@ConfigurationProperties(prefix = "salome.hub-crm.whatsapp")
public record HubCrmWhatsappProperties(
        long firstQuoteId,
        int waitDays
) {
    public HubCrmWhatsappProperties {
        if (waitDays <= 0) waitDays = 3;
    }
}
