package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Bipagem de uma NF na descarga de coleta (vira pré-CTe até o CT-e ser emitido).
 * {@code idLocalDestino} é o box destino escolhido na hora (Transferência ou Separação).
 */
public record BiparNfRequest(
        @NotBlank String chaveNf,
        String numeroNf,
        String serie,
        String cnpjEmitente,
        Integer volumes,
        BigDecimal peso,
        @NotNull Long idLocalDestino
) {
}
