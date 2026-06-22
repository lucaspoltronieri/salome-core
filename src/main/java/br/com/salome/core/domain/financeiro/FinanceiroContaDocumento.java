package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Folha do drill de um card de horizonte: um documento (conta a pagar ou a receber) individual.
 * Permite abrir o detalhe estilo Sankhya ate o lancamento especifico.
 */
public record FinanceiroContaDocumento(
        String documento,
        String clienteFornecedor,
        String filial,
        String banco,
        LocalDate dataVencimento,
        BigDecimal valor,
        String origemTipo
) {
}
