package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Visão de uma atividade com seus participantes (para app e painel).
 */
public record AtividadeResumo(
        long id,
        int idFilial,
        TipoAtividade tipo,
        String subtipo,
        StatusAtividade status,
        Long idViagemLegado,
        String placaVeiculo,
        Instant iniciadaEm,
        Instant finalizadaEm,
        List<Participante> participantes,
        Integer qtdCtes,
        BigDecimal volumes,
        BigDecimal peso
) {
    public AtividadeResumo(long id,
                           int idFilial,
                           TipoAtividade tipo,
                           String subtipo,
                           StatusAtividade status,
                           Long idViagemLegado,
                           String placaVeiculo,
                           Instant iniciadaEm,
                           Instant finalizadaEm,
                           List<Participante> participantes) {
        this(id, idFilial, tipo, subtipo, status, idViagemLegado, placaVeiculo,
                iniciadaEm, finalizadaEm, participantes, null, null, null);
    }

    public long participantesAtivos() {
        return participantes.stream().filter(p -> p.saidaEm() == null).count();
    }
}
