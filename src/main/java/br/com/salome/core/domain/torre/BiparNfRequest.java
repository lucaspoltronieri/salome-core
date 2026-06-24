package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Bipagem de uma NF na descarga de coleta (vira pré-CTe até o CT-e ser emitido).
 */
public record BiparNfRequest(
        @NotBlank String chaveNf,
        String numeroNf,
        String serie,
        String cnpjEmitente,
        Integer volumes,
        BigDecimal peso
) {
}
