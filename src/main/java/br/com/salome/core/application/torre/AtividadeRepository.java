package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.CaminhaoEmDescarga;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AtividadeRepository {

    long inserir(Atividade atividade);

    Optional<Atividade> buscar(long id, int idFilial);

    List<Atividade> listarAbertas(int idFilial);

    void finalizar(long id, Instant finalizadaEm);

    /** Marca como CANCELADA, encerra o ciclo (finalizada_em) e grava o motivo na observação. */
    void cancelar(long id, Instant canceladaEm, String motivo);

    /** Ids de viagens (legado) que já têm descarga de transferência na filial (qualquer status). */
    Set<Long> idsViagensComDescarga(int idFilial);

    /**
     * Caminhões com descarga de transferência aberta ou finalizada desde {@code finalizadaDesde}
     * (ex.: começo do dia) — para escolher qual caminhão separar. Default vazio para fakes de teste.
     */
    default List<CaminhaoEmDescarga> listarCaminhoesEmDescarga(int idFilial, Instant finalizadaDesde) {
        return List.of();
    }
}
