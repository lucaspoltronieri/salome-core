package br.com.salome.core.domain.torre.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String login,
        @NotBlank String senha
) {
}
