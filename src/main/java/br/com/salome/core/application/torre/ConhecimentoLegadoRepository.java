package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.CteDescarga;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Data de emissão ({@code conhecimento.cteEmissao}) por idConhecimento, em lote.
     * Usado para enriquecer a tela "Armazém atual" (a Torre não guarda essa data).
     * Default vazio para fakes de teste; a implementação JDBC sobrescreve.
     */
    default Map<Long, LocalDate> emissaoPorConhecimento(Collection<Long> idsConhecimento) {
        return Map.of();
    }
}
