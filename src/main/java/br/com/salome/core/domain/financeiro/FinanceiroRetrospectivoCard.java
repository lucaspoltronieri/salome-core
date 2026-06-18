package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.util.List;

/**
 * Card retrospectivo (passado) do Fluxo de Caixa: o que de fato entrou/saiu no periodo do filtro,
 * sem comparacao previsto x realizado. Ex.: recebido de clientes, pago a fornecedores, pago via
 * extrato bancario, pago em dinheiro. Traz {@link #contas} para o drill por plano de contas, igual
 * aos cards de horizonte.
 */
public record FinanceiroRetrospectivoCard(
        String codigo,
        String titulo,
        BigDecimal valor,
        int quantidade,
        String detalhe,
        String tom,
        List<FinanceiroContaNo> contas
) {
}
