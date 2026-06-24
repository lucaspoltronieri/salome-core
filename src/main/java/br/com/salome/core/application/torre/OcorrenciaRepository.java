package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Ocorrencia;
import java.util.List;
import java.util.Optional;

public interface OcorrenciaRepository {

    long inserir(Ocorrencia ocorrencia);

    Optional<Ocorrencia> buscar(long id, int idFilial);

    List<Ocorrencia> listarPorFilial(int idFilial, int limite);
}
