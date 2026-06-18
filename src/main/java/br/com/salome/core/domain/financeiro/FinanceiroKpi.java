package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record FinanceiroKpi(
        String titulo,
        BigDecimal valor,
        String detalhe,
        String tom
) {
}
