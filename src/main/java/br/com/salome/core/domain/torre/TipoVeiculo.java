package br.com.salome.core.domain.torre;

import java.util.Optional;

/**
 * Tipo do veículo de saída, conforme o fluxo de carregamento que ele atende.
 */
public enum TipoVeiculo {

    ENTREGA,
    TRANSFERENCIA;

    public static Optional<TipoVeiculo> porCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (TipoVeiculo t : values()) {
            if (t.name().equalsIgnoreCase(codigo.trim())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
