package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.FilialTorre;
import java.util.List;
import java.util.Optional;

public interface FilialTorreRepository {

    List<FilialTorre> listarAtivas();

    /** Todas as filiais cadastradas (inclui inativas) — visão de admin. */
    List<FilialTorre> listarTodas();

    Optional<FilialTorre> buscar(int idFilial);

    /** Cria ou atualiza a filial (upsert por idFilial). */
    void salvar(FilialTorre filial);
}
