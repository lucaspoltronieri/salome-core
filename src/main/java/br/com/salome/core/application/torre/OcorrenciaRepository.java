package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.OcorrenciaCte;
import br.com.salome.core.domain.torre.OcorrenciaDetalhe;
import br.com.salome.core.domain.torre.OcorrenciaFoto;
import br.com.salome.core.domain.torre.OcorrenciaItem;
import java.util.List;
import java.util.Optional;

public interface OcorrenciaRepository {

    long inserir(Ocorrencia ocorrencia);

    /**
     * Insere o cabeçalho e os filhos (CT-es, fotos, itens) numa transação; retorna o id.
     */
    long inserirDetalhe(Ocorrencia cabecalho, List<OcorrenciaCte> ctes,
                        List<OcorrenciaFoto> fotos, List<OcorrenciaItem> itens);

    Optional<Ocorrencia> buscar(long id, int idFilial);

    Optional<OcorrenciaDetalhe> buscarDetalhe(long id, int idFilial);

    List<Ocorrencia> listarPorFilial(int idFilial, int limite);

    /** Lista com filtros opcionais (status/tipo nulos = sem filtro). */
    List<Ocorrencia> listar(int idFilial, String status, String tipo, int limite);

    /** Aplica o tratamento (campos já mesclados) e marca tratada_por/tratada_em. */
    void atualizarTratamento(Ocorrencia cabecalho);

    List<OcorrenciaFoto> fotos(long idOcorrencia);
}
