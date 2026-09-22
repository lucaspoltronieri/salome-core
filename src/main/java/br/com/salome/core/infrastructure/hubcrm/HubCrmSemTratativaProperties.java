package br.com.salome.core.infrastructure.hubcrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Baixa automática da cotação sem tratativa do comercial (decisão do Lucas, 22/09/2026): proposta
 * ABERTA há mais de {@code days} dias, sem nenhuma atividade no card do ArpaSuite, vira NÃO
 * APROVADA no legado (motivo Preço, que é o que o legado tem) e perdida no ArpaSuite com o motivo
 * {@code lostReasonId} ("Sem tratativa do comercial, baixado pelo legado").
 */
@ConfigurationProperties(prefix = "salome.hub-crm.sem-tratativa")
public record HubCrmSemTratativaProperties(boolean enabled, int days, long lostReasonId, String description) {
    public static final long DEFAULT_LOST_REASON_ID = 317833;
    public static final String DEFAULT_DESCRIPTION = "Sem tratativa do comercial, baixado pelo legado";

    public HubCrmSemTratativaProperties {
        if (days <= 0) days = 10;
        if (lostReasonId <= 0) lostReasonId = DEFAULT_LOST_REASON_ID;
        if (description == null || description.isBlank()) description = DEFAULT_DESCRIPTION;
    }

    public static HubCrmSemTratativaProperties defaults() {
        return new HubCrmSemTratativaProperties(false, 10, DEFAULT_LOST_REASON_ID, DEFAULT_DESCRIPTION);
    }
}
