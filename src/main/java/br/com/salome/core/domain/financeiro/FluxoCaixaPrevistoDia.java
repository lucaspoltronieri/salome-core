package br.com.salome.core.domain.financeiro;

import br.com.salome.core.domain.notacompra.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FluxoCaixaPrevistoDia(
        LocalDate data,
        BigDecimal saldoInicial,
        BigDecimal entradasPrevistas,
        BigDecimal entradasRealizadas,
        BigDecimal saidasPrevistas,
        BigDecimal saidasRealizadas,
        BigDecimal saldoProjetado,
        BigDecimal saldoRealizado,
        long quantidadePrevista,
        long quantidadeRealizada,
        List<FluxoCaixaPrevistoLancamento> lancamentos,
        LegacyOrigin origin
) {
}
