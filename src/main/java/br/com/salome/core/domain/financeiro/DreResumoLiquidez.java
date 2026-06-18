package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record DreResumoLiquidez(
        String titulo,
        BigDecimal valor,
        String detalhe,
        String tom
) {
}
