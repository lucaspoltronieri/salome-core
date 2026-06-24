package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de local físico do armazém (ação ADMIN), escopado à filial ativa.
 * {@code tipo}: DOCA | BOX | AREA | PENDENCIA | AVARIA | QUIMICA | BOX_DEFINITIVO.
 */
public record CriarLocalRequest(
        @NotBlank @Size(max = 40) String codigo,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 40) String tipo
) {
}
