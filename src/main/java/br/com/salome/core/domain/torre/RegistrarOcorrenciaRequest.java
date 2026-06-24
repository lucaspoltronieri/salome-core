package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;

public record RegistrarOcorrenciaRequest(
        @NotBlank String tipo,
        Long idDocumento,
        Long idAtividade,
        String placa,
        String descricao
) {
}
