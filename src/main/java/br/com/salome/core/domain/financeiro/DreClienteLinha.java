package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Uma linha do ranking do DRE por cliente: receita realizada do cliente, peso de rateio aplicado
 * (% conforme o criterio), despesa apropriada (bolo x peso) e resultado/margem. As colunas
 * {@code toneladas} e {@code qtdCtes} sao informativas (direcionadores apurados pela emissao).
 */
public record DreClienteLinha(
        Integer idCliente,
        String nome,
        BigDecimal receita,
        BigDecimal pesoRateioPct,
        BigDecimal toneladas,
        int qtdCtes,
        BigDecimal despesaApropriada,
        BigDecimal resultado,
        BigDecimal margemPct
) {
}
