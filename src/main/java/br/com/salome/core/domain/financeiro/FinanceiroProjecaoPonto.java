package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ponto diario da curva de saldo projetado: parte do saldo bancario atual e acumula entradas
 * (recebimentos previstos) menos saidas (pagamentos previstos) dia a dia.
 */
public record FinanceiroProjecaoPonto(
        LocalDate data,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldoProjetado
) {
}
