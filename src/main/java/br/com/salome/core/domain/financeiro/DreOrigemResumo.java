package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Resumo da origem de uma despesa/receita dentro de uma conta analitica do DRE:
 * de onde o lancamento veio (Nota de compra, Extrato, Caixa, Pagamento, Fatura).
 */
public record DreOrigemResumo(
        String origemTipo,
        String label,
        BigDecimal valor,
        int quantidade
) {
}
