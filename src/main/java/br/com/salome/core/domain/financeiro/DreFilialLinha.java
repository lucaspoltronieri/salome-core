package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Uma linha do ranking do DRE por filial: receita realizada da filial (emissao do CT-e), despesa
 * direta (movimentos com {@code filialId} da propria filial), overhead rateado (parte do bolo sem
 * filial distribuida por receita) e resultado/margem.
 *
 * <p>O {@code resultado} (= receita - despesa direta - overhead) reconcilia com o DRE caixa. Sobre
 * ele entram dois ajustes gerenciais: o {@code pedagio} (custo off-book, vale-pedagio por placa) e o
 * acerto inter-filial ({@code repasseRecebido} de outras filiais por entregas feitas, menos
 * {@code repassePago} a outras por entregas dos CT-es desta filial - zero-sum entre filiais). O
 * {@code resultadoAjustado} = resultado - pedagio + repasseRecebido - repassePago.
 */
public record DreFilialLinha(
        Integer idFilial,
        String nome,
        BigDecimal receita,
        BigDecimal despesaDireta,
        BigDecimal overhead,
        BigDecimal despesaTotal,
        BigDecimal resultado,
        BigDecimal margemPct,
        BigDecimal pedagio,
        BigDecimal repasseRecebido,
        BigDecimal repassePago,
        BigDecimal transferenciaAjuste,
        BigDecimal resultadoAjustado,
        BigDecimal margemAjustadaPct
) {
}
