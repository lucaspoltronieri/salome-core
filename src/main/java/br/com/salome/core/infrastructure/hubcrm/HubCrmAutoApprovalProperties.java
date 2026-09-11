package br.com.salome.core.infrastructure.hubcrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Aprovação automática da cotação no legado a partir do CT-e emitido. Fica separada de
 * {@link HubCrmProperties} porque é a única parte do Hub que escreve no legado (usuário
 * {@code crm_api}) e precisa poder ser ligada/desligada sozinha.
 */
@ConfigurationProperties(prefix = "salome.hub-crm.auto-approval")
public record HubCrmAutoApprovalProperties(
        boolean enabled,
        int windowDays,
        int lookbackDays,
        String legacyUrl,
        String legacyUsername,
        String legacyPassword,
        String approvalType
) {
    public HubCrmAutoApprovalProperties {
        if (windowDays <= 0) windowDays = 30;
        if (lookbackDays <= 0) lookbackDays = 3;
    }
}
