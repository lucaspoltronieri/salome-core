package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record DreLinha(
        String codigo,
        String titulo,
        BigDecimal valor,
        BigDecimal percentualReceitaLiquida,
        int quantidade,
        boolean calculada,
        boolean alerta
) {
}
