package br.com.salome.core.domain.financeiro;

import java.util.List;

public record FluxoCaixaPrevistoSnapshot(
        FluxoCaixaPrevistoFiltro filtro,
        FluxoCaixaPrevistoResumo resumo,
        List<FluxoCaixaPrevistoDia> timeline,
        List<FluxoCaixaPrevistoLancamento> lancamentos
) {
}
