package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot da tela Fluxo de Caixa estrategico (voltada ao futuro). Exclusivo desta tela. Os KPIs e
 * os cards de horizonte ({@link #aPagar}/{@link #aReceber}) projetam o que ha a vencer a partir de
 * hoje; {@link #projecao} concilia esses previstos com o {@link #saldoBancarioAtual}; o
 * {@link #retrospectivo} mostra o que de fato entrou/saiu no periodo do filtro.
 */
public record FinanceiroDashboardSnapshot(
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        List<FinanceiroKpi> kpis,
        BigDecimal saldoBancarioAtual,
        List<FinanceiroHorizonteCard> aPagar,
        List<FinanceiroHorizonteCard> aReceber,
        List<FinanceiroHorizonteCard> faturamentoPendente,
        List<FinanceiroProjecaoPonto> projecao,
        List<FinanceiroRetrospectivoCard> retrospectivo,
        List<FinanceiroGrupo> porCentroCusto,
        List<FinanceiroGrupo> porBanco,
        List<FinanceiroSaldoBanco> saldosBancarios,
        List<FinanceiroGrupo> rankingClientesFornecedores,
        List<FinanceiroMovimento> movimentos,
        List<String> alertas,
        boolean demonstrativo
) {
}
