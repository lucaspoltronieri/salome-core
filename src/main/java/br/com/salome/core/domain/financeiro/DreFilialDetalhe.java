package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DRE detalhado de uma unica filial: secoes RECEITA/DEDUCOES/despesas montadas dos proprios
 * movimentos da filial (valores reais, nao escalados, pois a despesa ja carrega {@code filialId}),
 * mais o overhead rateado (bolo sem filial) e o pedagio off-book entrando como ajustes antes do
 * resultado. Reaproveita {@link DreSecao}/{@link DreLinha}/{@link DreResumoLiquidez} para renderizar
 * igual ao DRE caixa.
 */
public record DreFilialDetalhe(
        Integer idFilial,
        String nome,
        String regime,
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
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
        BigDecimal margemAjustadaPct,
        List<DreResumoLiquidez> resumos,
        List<DreLinha> linhas,
        List<DreSecao> secoes,
        List<String> alertas,
        boolean demonstrativo
) {
}
