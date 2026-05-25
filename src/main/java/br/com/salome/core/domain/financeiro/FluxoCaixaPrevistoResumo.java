package br.com.salome.core.domain.financeiro;

import br.com.salome.core.domain.notacompra.LegacyOrigin;
import java.math.BigDecimal;

public record FluxoCaixaPrevistoResumo(
        BigDecimal saldoInicial,
        BigDecimal saldoFinalProjetado,
        BigDecimal saldoFinalRealizado,
        BigDecimal totalEntradasPrevistas,
        BigDecimal totalEntradasRealizadas,
        BigDecimal totalSaidasPrevistas,
        BigDecimal totalSaidasRealizadas,
        long quantidadeLancamentos,
        long quantidadePrevistos,
        long quantidadeRealizados,
        LegacyOrigin origin
) {
}
