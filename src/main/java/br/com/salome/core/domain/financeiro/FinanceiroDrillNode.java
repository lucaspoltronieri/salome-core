package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * No generico de drill-down para os paineis Clientes (receita) e Fornecedores (despesa).
 * Cliente -> Faturas -> CTEs; Fornecedor -> Notas de compra -> Produtos.
 *
 * @param id        identificador usado para buscar o proximo nivel (idCliente, idFatura, idFornecedor, idNotaCompra)
 * @param titulo    rotulo principal (nome, documento, descricao)
 * @param detalhe   rotulo secundario (status, data, vencimento)
 * @param valor     valor monetario do no
 * @param quantidade quantidade de itens/filhos agregados
 * @param temFilhos se o no pode ser expandido em outro nivel
 */
public record FinanceiroDrillNode(
        String id,
        String titulo,
        String detalhe,
        BigDecimal valor,
        int quantidade,
        boolean temFilhos
) {
    public FinanceiroDrillNode {
        valor = valor == null ? BigDecimal.ZERO : valor;
    }
}
