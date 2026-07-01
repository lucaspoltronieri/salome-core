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

    /**
     * Ids de viagens (legado) cuja separação já foi concluída (FINALIZADA) na filial — ou seja,
     * já foram separadas uma vez e não devem reaparecer na lista de caminhões a separar.
     * Default vazio para fakes de teste.
     */
    default Set<Long> idsViagensComSeparacaoConcluida(int idFilial) {
        return Set.of();
    }

    /**
     * Separação ABERTA daquela viagem na filial, se houver — para reaproveitar a atividade
     * (2º operador entra na mesma) em vez de criar outra. Default vazio para fakes de teste.
     */
    default Optional<Atividade> buscarSeparacaoAbertaDaViagem(int idFilial, long idViagem) {
        return Optional.empty();
    }
}
