package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DRE detalhado de um unico cliente: receita real do cliente (secoes RECEITA/DEDUCOES montadas dos
 * proprios movimentos do cliente) e as secoes de despesa do bolo escaladas pelo peso de rateio do
 * cliente ({@code pesoRateioPct}). Reaproveita {@link DreSecao}/{@link DreLinha}/{@link DreResumoLiquidez}
 * para renderizar igual ao DRE caixa.
 */
public record DreClienteDetalhe(
        Integer idCliente,
        String nome,
        String regime,
        String driver,
        LocalDate inicio,
        LocalDate fim,
        Instant atualizadoEm,
        BigDecimal receita,
        BigDecimal despesaApropriada,
        BigDecimal resultado,
        BigDecimal margemPct,
        BigDecimal pesoRateioPct,
        BigDecimal toneladas,
        int qtdCtes,
        List<DreResumoLiquidez> resumos,
        List<DreLinha> linhas,
        List<DreSecao> secoes,
        List<String> alertas,
        boolean demonstrativo
) {
}
