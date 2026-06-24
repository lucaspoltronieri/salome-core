package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.FilialTorre;
import java.util.List;
import java.util.Optional;

public interface FilialTorreRepository {

    List<FilialTorre> listarAtivas();

    Optional<FilialTorre> buscar(int idFilial);
}
