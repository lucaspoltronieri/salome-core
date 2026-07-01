package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/** Agregado simples (contagem + volumes + peso) para blocos do painel TV. */
public record AgregadoOperacional(
        int qtd,
        int volumes,
        BigDecimal peso
) {
    public static AgregadoOperacional vazio() {
        return new AgregadoOperacional(0, 0, BigDecimal.ZERO);
    }
}
