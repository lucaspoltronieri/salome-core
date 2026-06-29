package br.com.salome.core.domain.torre;

import java.time.Instant;
import java.util.List;

/**
 * Foto do armazém de uma filial pelos estados próprios da Torre: o que está
 * fisicamente no galpão, agrupado por box, mais a lista plana de documentos para
 * a tabela detalhada. Diferente do "mapa" (que lê o status grosso do legado),
 * aqui a fonte da verdade são os estados finos que o app de armazém controla.
 */
public record ArmazemSnapshot(
        int idFilial,
        Instant atualizadoEm,
        List<BoxOcupacao> boxes,
        List<DocumentoArmazenado> documentos
) {
}
