package br.com.salome.core.domain.torre;

import java.time.Instant;

public record Ocorrencia(
        Long id,
        int idFilial,
        String tipo,
        Long idDocumento,
        Long idAtividade,
        String placaVeiculo,
        String descricao,
        String fotoPath,
        Long idUsuario,
        Instant criadoEm
) {
}
