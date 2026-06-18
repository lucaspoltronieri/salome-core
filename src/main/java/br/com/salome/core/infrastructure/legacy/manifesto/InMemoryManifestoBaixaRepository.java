package br.com.salome.core.infrastructure.legacy.manifesto;

import br.com.salome.core.application.manifesto.ManifestoBaixaRepository;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class InMemoryManifestoBaixaRepository implements ManifestoBaixaRepository {

    @Override
    public Optional<Integer> buscarFilialDestinoSaoJoseRioPreto() {
        return Optional.of(2);
    }

    @Override
    public List<CteMapaSjpRecord> listarArmazemSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        return List.of();
    }

    @Override
    public List<CteMapaSjpRecord> listarEmRotaEntrega(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        return List.of();
    }

    @Override
    public List<CteMapaSjpRecord> listarOutrosArmazens(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        return List.of();
    }

    @Override
    public List<CteMapaSjpRecord> listarViagensParaSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        return List.of();
    }
}
