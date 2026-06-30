package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/**
 * Documento operacional já resolvido com o código do box atual (SEP/DIST/TRANSF).
 * Projeção de leitura para as telas de carregamento e separação, que precisam
 * agrupar os CT-es por box. Os nomes dos campos espelham o JSON consumido pelo app.
 */
public record DocumentoComLocal(
        Long id,
        Integer numeroCte,
        Long idConhecimentoLegado,
        boolean preCte,
        Integer volumes,
        BigDecimal peso,
        String remetente,
        String destinatario,
        String cidadeDestino,
        String chaveNf,
        StatusDocumento status,
        Long idLocalAtual,
        String codigoLocal
) {
}
