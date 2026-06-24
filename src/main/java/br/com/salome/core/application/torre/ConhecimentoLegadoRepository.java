package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.CteDescarga;
import java.util.List;
import java.util.Optional;

/**
 * Leitura dos CT-es de uma viagem de transferência no legado (somente leitura).
 */
public interface ConhecimentoLegadoRepository {

    List<CteDescarga> listarCtesDaViagem(long idViagemTransferencia);

    Optional<CteDescarga> buscarCte(long idConhecimento);

    /** Procura o CT-e que já contém a NF (pela chave de acesso) — usado no casamento de pré-CTe de coleta. */
    Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe);
}
