package br.com.salome.core.domain.torre;

import java.time.Instant;

/**
 * Participação individual de uma pessoa numa atividade (tempo entrada/saída).
 */
public record Participante(
        Long id,
        long idAtividade,
        long idUsuario,
        String nomeUsuario,
        String funcao,
        Instant entradaEm,
        Instant saidaEm,
        String dispositivo,
        String origem
) {
}
