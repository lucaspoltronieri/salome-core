package br.com.salome.core.domain.torre;

import java.time.Instant;

/**
 * Cabeçalho de uma atividade operacional do armazém.
 */
public record Atividade(
        Long id,
        int idFilial,
        TipoAtividade tipo,
        String subtipo,
        StatusAtividade status,
        Long idViagemLegado,
        String placaVeiculo,
        Long idResponsavel,
        String observacao,
        Instant iniciadaEm,
        Instant finalizadaEm
) {
}
