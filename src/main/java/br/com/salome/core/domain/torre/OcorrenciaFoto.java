package br.com.salome.core.domain.torre;

/** Foto de uma ocorrência. {@code categoria} = AVARIA | NOTA_FISCAL. */
public record OcorrenciaFoto(
        Long id,
        String categoria,
        String fotoPath,
        int ordem
) {
}
