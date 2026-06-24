package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.LocalArmazem;
import java.util.List;
import java.util.Optional;

public interface LocalArmazemRepository {

    List<LocalArmazem> listarAtivos(int idFilial);

    /** Todos os locais da filial (inclui inativos) — visão de admin. */
    List<LocalArmazem> listarTodos(int idFilial);

    Optional<LocalArmazem> buscar(long id, int idFilial);

    long criar(LocalArmazem local);

    /** Ativa/desativa o local da filial. Retorna true se algum registro foi alterado. */
    boolean definirAtivo(long id, int idFilial, boolean ativo);
}
