package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoFiltro;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoLancamento;
import java.math.BigDecimal;
import java.util.List;

public interface FluxoCaixaPrevistoRepository {

    BigDecimal consultarSaldoInicial(FluxoCaixaPrevistoFiltro filtro);

    List<FluxoCaixaPrevistoLancamento> listarLancamentos(FluxoCaixaPrevistoFiltro filtro);
}
