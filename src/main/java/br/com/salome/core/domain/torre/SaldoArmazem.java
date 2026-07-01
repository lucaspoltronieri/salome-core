package br.com.salome.core.domain.torre;

/**
 * Saldo atual do armazém de uma filial, com o total e a quebra por etapa do fluxo
 * físico (para o bloco "No armazém" do painel TV). Cada etapa é um
 * {@link AgregadoOperacional} (qtd de CT-es + volumes + peso).
 */
public record SaldoArmazem(
        AgregadoOperacional total,
        AgregadoOperacional noArmazem,
        AgregadoOperacional emSeparacao,
        AgregadoOperacional separadoBox,
        AgregadoOperacional emCarregamento
) {
}
