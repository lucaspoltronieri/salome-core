package br.com.salome.core.domain.torre;

import java.util.List;

/** Agregado de uma ocorrência com seus CT-es, fotos e itens (tela de detalhe web). */
public record OcorrenciaDetalhe(
        Ocorrencia cabecalho,
        List<OcorrenciaCte> ctes,
        List<OcorrenciaFoto> fotos,
        List<OcorrenciaItem> itens
) {
}
