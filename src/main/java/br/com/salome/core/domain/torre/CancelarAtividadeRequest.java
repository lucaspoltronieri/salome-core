package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cancelamento de atividade exige justificativa (vai para a auditoria e a
 * observação da atividade).
 */
public record CancelarAtividadeRequest(
        @NotBlank(message = "Informe o motivo do cancelamento.")
        @Size(max = 500, message = "Motivo muito longo (máx. 500).")
        String motivo
) {
}
