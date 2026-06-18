package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DreGerencialSnapshot(
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        List<DreResumoLiquidez> resumos,
        List<DreLinha> linhas,
        List<DreSecao> secoes,
        List<FinanceiroGrupo> porFilial,
        List<FinanceiroGrupo> porPlano,
        List<FinanceiroGrupo> porCentroCusto,
        List<FinanceiroMovimento> movimentos,
        List<String> alertas,
        int receitasExcluidasQuantidade,
        BigDecimal receitasExcluidasValor,
        boolean demonstrativo,
        BigDecimal custoTotalPeriodo,
        BigDecimal toneladasTransportadas,
        BigDecimal custoPorTonelada
) {
}
