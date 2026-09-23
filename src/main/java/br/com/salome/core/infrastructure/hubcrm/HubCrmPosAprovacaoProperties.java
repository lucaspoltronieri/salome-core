package br.com.salome.core.infrastructure.hubcrm;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acompanhamento da cotação depois de aprovada (decisão do Lucas, 23/09/2026), agora que o
 * comercial volta a aprovar à mão e o legado lança a coleta na aprovação.
 *
 * <ul>
 *   <li>{@code bindingEnabled}: o Hub segue lendo os CT-es e amarra o que aparecer à cotação já
 *       aprovada, pela coleta ({@code coleta.idCotacao} → {@code conhecimento.idColeta}) ou pelas
 *       regras de sempre. Só leitura no legado.</li>
 *   <li>{@code triagemEnabled}: aprovada há {@code days} dias sem CT-e e sem coleta em andamento
 *       volta a NÃO APROVADA, com o motivo Arrependimento do frete. <b>Grava no legado.</b></li>
 *   <li>{@code activationDate}: a triagem só alcança cotações aprovadas a partir daí. Vazio, vale a
 *       data em que a regra for ligada, gravada uma vez no checkpoint — o histórico antigo fica como
 *       está.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "salome.hub-crm.pos-aprovacao")
public record HubCrmPosAprovacaoProperties(boolean bindingEnabled, boolean triagemEnabled, int days,
        LocalDate activationDate, String description) {
    /** Checkpoint com a data em que a triagem foi ligada. */
    public static final String ACTIVATION_CHECKPOINT = "pos-aprovacao:ativacao";
    public static final String DEFAULT_DESCRIPTION =
            "Aprovada sem CT-e emitido no prazo; emitindo o CT-e, reaprove";
    /** Teto da leitura de aprovadas: acima disso a cotação já não interessa ao acompanhamento. */
    public static final int MAX_LOOKBACK_DAYS = 120;

    public HubCrmPosAprovacaoProperties {
        if (days <= 0) days = 10;
        if (description == null || description.isBlank()) description = DEFAULT_DESCRIPTION;
    }

    public static HubCrmPosAprovacaoProperties defaults() {
        return new HubCrmPosAprovacaoProperties(false, false, 10, null, DEFAULT_DESCRIPTION);
    }
}
