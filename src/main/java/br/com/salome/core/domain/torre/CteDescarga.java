package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/**
 * CT-e de uma viagem, como visto na descarga (lido do legado).
 */
public record CteDescarga(
        long idConhecimento,
        Integer cte,
        String notasFiscais,
        BigDecimal volumes,
        BigDecimal peso,
        String remetente,
        String destinatario,
        String cidadeDestino
) {
}
