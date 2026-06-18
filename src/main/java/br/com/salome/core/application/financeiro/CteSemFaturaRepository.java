package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.CteSemFaturaExportRow;
import java.time.LocalDate;
import java.util.List;

public interface CteSemFaturaRepository {

    List<CteSemFaturaExportRow> listarEmitidosSemFaturaAte(LocalDate ate);
}
