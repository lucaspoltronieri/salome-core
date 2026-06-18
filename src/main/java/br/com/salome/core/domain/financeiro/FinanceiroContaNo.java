package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.util.List;

/**
 * No da arvore de plano de contas usada no drill dos cards de horizonte do Fluxo de Caixa.
 * Estrutura propria desta tela (nao reusa {@link DreContaNo}) para manter o DRE isolado. Nos
 * sinteticos sao rollup dos filhos; nos analiticos (folha) trazem os {@link #documentos}.
 */
public record FinanceiroContaNo(
        String classificacao,
        String descricao,
        boolean sintetica,
        int nivel,
        BigDecimal valor,
        int quantidade,
        List<FinanceiroContaDocumento> documentos,
        List<FinanceiroContaNo> filhos
) {
}
