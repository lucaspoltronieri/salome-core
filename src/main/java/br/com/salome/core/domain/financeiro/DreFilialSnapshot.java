package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do DRE por filial (ranking): receita realizada por filial emissora menos a despesa
 * direta da filial e o overhead (bolo sem filial) rateado por receita. O {@code resultadoTotal}
 * (sem pedagio) fecha com o resultado liquido do DRE caixa do mesmo periodo (reconciliacao).
 *
 * <p>O {@code pedagioTotal} e um custo off-book (vale-pedagio por placa, fora do bolo do legado); o
 * {@code resultadoAjustadoTotal} (= resultadoTotal - pedagioTotal) ja considera esse custo.
 */
public record DreFilialSnapshot(
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        String regime,
        BigDecimal receitaTotal,
        BigDecimal despesaDiretaTotal,
        BigDecimal overheadTotal,
        BigDecimal despesaTotal,
        BigDecimal resultadoTotal,
        BigDecimal margemMediaPct,
        BigDecimal pedagioTotal,
        BigDecimal repasseTotal,
        BigDecimal repassePercentualPct,
        BigDecimal transferenciaTotal,
        BigDecimal resultadoAjustadoTotal,
        int filiais,
        List<DreFilialLinha> linhas,
        List<String> alertas,
        boolean demonstrativo
) {
}
