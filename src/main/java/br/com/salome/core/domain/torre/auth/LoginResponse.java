package br.com.salome.core.domain.torre.auth;

import java.time.Instant;

public record LoginResponse(
        String token,
        Instant expiraEm,
        UsuarioAutenticado usuario
) {
}
