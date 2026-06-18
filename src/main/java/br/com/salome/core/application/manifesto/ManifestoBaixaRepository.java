package br.com.salome.core.application.manifesto;

import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ManifestoBaixaRepository {

    Optional<Integer> buscarFilialDestinoSaoJoseRioPreto();

    List<CteMapaSjpRecord> listarArmazemSjp(Integer filialDestinoId, LocalDate dataCorte, int limite);

    List<CteMapaSjpRecord> listarEmRotaEntrega(Integer filialDestinoId, LocalDate dataCorte, int limite);

    List<CteMapaSjpRecord> listarOutrosArmazens(Integer filialDestinoId, LocalDate dataCorte, int limite);

    List<CteMapaSjpRecord> listarViagensParaSjp(Integer filialDestinoId, LocalDate dataCorte, int limite);
}
