package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do DRE por cliente (ranking) em regime de caixa: receita realizada direta por cliente
 * menos o rateio do bolo de despesas (mesmos centros de custo do DRE caixa) pelo criterio escolhido
 * ({@code driver} = PESO | FRETE | CTES). Por construcao a soma dos resultados por cliente fecha com
 * o resultado liquido do DRE caixa do mesmo periodo (reconciliacao).
 */
public record DreClienteSnapshot(
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        String regime,
        String driver,
        BigDecimal receitaTotal,
        BigDecimal despesaTotal,
        BigDecimal resultadoTotal,
        BigDecimal margemMediaPct,
        int clientes,
        BigDecimal toneladasTotal,
        int qtdCtesTotal,
        BigDecimal custoPorTonelada,
        BigDecimal toneladasNaoAtribuidas,
        BigDecimal despesaNaoAtribuida,
        List<DreClienteLinha> linhas,
        List<String> alertas,
        boolean demonstrativo
) {
}
