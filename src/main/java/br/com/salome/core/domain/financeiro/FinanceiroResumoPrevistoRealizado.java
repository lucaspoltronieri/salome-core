package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

public record FinanceiroResumoPrevistoRealizado(
        String titulo,
        BigDecimal previsto,
        BigDecimal realizado,
        BigDecimal diferenca,
        String detalhe,
        String tom
) {
}
