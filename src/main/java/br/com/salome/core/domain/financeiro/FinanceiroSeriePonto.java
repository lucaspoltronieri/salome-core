package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceiroSeriePonto(
        LocalDate data,
        BigDecimal receitasPrevistas,
        BigDecimal receitasRealizadas,
        BigDecimal despesasPrevistas,
        BigDecimal despesasRealizadas,
        BigDecimal saldoPrevisto,
        BigDecimal saldoRealizado
) {
}
