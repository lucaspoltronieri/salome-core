package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.util.List;

/**
 * Secao do DRE fiel ao legado (Receita, Deducoes, Operacional/Custos, Comercial, Administrativo,
 * Depreciacao, Financeiro, Impostos, Transferencia). Cada secao traz a arvore do plano de contas
 * com movimento no periodo.
 */
public record DreSecao(
        String codigo,
        String titulo,
        BigDecimal valor,
        BigDecimal percentualReceitaLiquida,
        int quantidade,
        boolean despesa,
        List<DreContaNo> contas
) {
}
