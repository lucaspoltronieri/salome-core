package br.com.salome.core.domain.financeiro;

/**
 * Conta do plano de contas legado (tabela {@code planocontas}).
 *
 * <p>Usado para rotular os nos sinteticos da arvore do DRE e para aplicar a correcao da aba
 * "Impostos e Financeiro" (flag {@code impostosFinanceiro} em {@code planocontascentrocusto}).
 */
public record PlanoConta(
        String classificacao,
        String descricao,
        boolean sintetica,
        boolean impostosFinanceiro
) {
}
