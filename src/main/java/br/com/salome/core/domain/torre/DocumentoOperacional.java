package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Documento operacional (CT-e) dentro da Torre.
 */
public record DocumentoOperacional(
        Long id,
        int idFilial,
        Integer numeroCte,
        Long idConhecimentoLegado,
        Long idViagemLegado,
        boolean preCte,
        Integer volumes,
        BigDecimal peso,
        String remetente,
        String destinatario,
        String cidadeDestino,
        String chaveNf,
        StatusDocumento status,
        Long idLocalAtual,
        Instant atualizadoEm
) {
}
