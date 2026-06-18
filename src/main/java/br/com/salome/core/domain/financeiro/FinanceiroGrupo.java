package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record FinanceiroGrupo(
        String chave,
        BigDecimal receitas,
        BigDecimal despesas,
        BigDecimal saldo,
        int quantidade
) {
}
