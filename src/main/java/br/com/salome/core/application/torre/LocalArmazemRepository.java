package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.LocalArmazem;
import java.util.List;
import java.util.Optional;

public interface LocalArmazemRepository {

    List<LocalArmazem> listarAtivos(int idFilial);

    Optional<LocalArmazem> buscar(long id, int idFilial);
}
