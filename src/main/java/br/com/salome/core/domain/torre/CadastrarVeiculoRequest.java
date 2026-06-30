package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cadastro de uma placa nova no app (operador). O tipo casa com o fluxo de
 * carregamento (Entrega ou Transferência).
 */
public record CadastrarVeiculoRequest(
        @NotBlank String placa,
        @NotNull TipoVeiculo tipo
) {
}
