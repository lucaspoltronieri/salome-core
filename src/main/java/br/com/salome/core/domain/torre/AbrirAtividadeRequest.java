package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotNull;

public record AbrirAtividadeRequest(
        @NotNull TipoAtividade tipo,
        String subtipo,
        Long idViagem,
        String placa,
        String funcao
) {
}
