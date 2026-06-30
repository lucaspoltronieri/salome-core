package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Bipagem de uma NF na coleta com crossdock direto: o pré-CTe entra direto no
 * carregamento de transferência indicado ({@code idAtividadeCarregamento}), já
 * EM_CARREGAMENTO, sem passar por box. O CT-e é casado depois pela chave da NF.
 */
public record BiparNfCrossdockRequest(
        @NotBlank String chaveNf,
        String numeroNf,
        String serie,
        String cnpjEmitente,
        Integer volumes,
        BigDecimal peso,
        @NotNull Long idAtividadeCarregamento
) {
}
