package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.CteDescarga;
import java.util.List;
import java.util.Optional;

/**
 * Leitura dos CT-es de uma viagem no legado (somente leitura).
 */
public interface ConhecimentoLegadoRepository {

    /** CT-es de todos os manifestos do caminhão (idViagem) — a descarga é por viagem. */
    List<CteDescarga> listarCtesDaViagem(long idViagem);

    Optional<CteDescarga> buscarCte(long idConhecimento);

    /** Procura o CT-e que já contém a NF (pela chave de acesso) — usado no casamento de pré-CTe de coleta. */
    Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe);
}
