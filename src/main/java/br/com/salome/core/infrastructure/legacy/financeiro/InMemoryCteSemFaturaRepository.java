package br.com.salome.core.infrastructure.legacy.financeiro;

import br.com.salome.core.application.financeiro.CteSemFaturaRepository;
import br.com.salome.core.domain.financeiro.CteSemFaturaExportRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class InMemoryCteSemFaturaRepository implements CteSemFaturaRepository {

    @Override
    public List<CteSemFaturaExportRow> listarEmitidosSemFaturaAte(LocalDate ate) {
        return List.of();
    }
}
