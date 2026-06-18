package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record FinanceiroSaldoBanco(
        Integer bancoId,
        String banco,
        BigDecimal saldoOperacional,
        BigDecimal saldoBancario,
        BigDecimal limite,
        boolean contaCaixa
) {
}
