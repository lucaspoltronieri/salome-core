package br.com.salome.core.domain.financeiro;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinanceiroDashboardSnapshot(
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        List<FinanceiroKpi> kpis,
        List<FinanceiroResumoPrevistoRealizado> resumos,
        List<FinanceiroSeriePonto> serie,
        List<FinanceiroGrupo> porDmr,
        List<FinanceiroGrupo> porCentroCusto,
        List<FinanceiroGrupo> porBanco,
        List<FinanceiroSaldoBanco> saldosBancarios,
        List<FinanceiroGrupo> rankingClientesFornecedores,
        List<FinanceiroMovimento> movimentos,
        List<String> alertas,
        boolean demonstrativo
) {
}
