package br.com.salome.core.domain.torre;

import java.time.Instant;

/**
 * Totais de uma atividade finalizada (cronometragem server-side).
 *
 * @param duracaoSegundos    tempo de parede da atividade (início -> fim)
 * @param horasHomemSegundos soma do tempo de todas as participações
 */
public record AtividadeFinalizada(
        long id,
        Instant iniciadaEm,
        Instant finalizadaEm,
        long duracaoSegundos,
        long horasHomemSegundos,
        int totalParticipantes
) {
}
