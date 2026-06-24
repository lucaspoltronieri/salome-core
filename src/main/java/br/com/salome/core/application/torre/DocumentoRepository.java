package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.StatusDocumento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository {

    /** Insere ou atualiza o documento (CT-e) da filial; retorna o id. */
    long salvar(DocumentoOperacional documento);

    Optional<DocumentoOperacional> buscar(long id, int idFilial);

    /** Documentos da filial em qualquer um dos status informados. */
    List<DocumentoOperacional> listarPorStatus(int idFilial, List<StatusDocumento> status);

    void atualizarStatusELocal(long id, StatusDocumento status, Long idLocalAtual, Instant em);

    /** Vincula o documento à atividade de forma idempotente; retorna nº de linhas inseridas (0 se já existia). */
    int vincularAtividade(long idAtividade, long idDocumento, String papel,
                          Integer volumes, BigDecimal peso, Long idUsuario, Instant em);

    void inserirMovimento(long idDocumento, String tipo, Long idAtividadeOrigem,
                          Long idAtividadeDestino, Long idLocalOrigem, Long idLocalDestino,
                          Long idUsuario, Instant em);

    /** Última atividade de descarga que descarregou o documento (para cross-dock). */
    Optional<Long> ultimaAtividadeDescarga(long idDocumento);

    List<DocumentoOperacional> listarPorAtividade(long idAtividade);

    void inserirNf(long idDocumento, String chaveNf, String numeroNf, String serie, String cnpjEmitente);

    /** Pré-CTes (coleta) ainda não casados com CT-e — fila de pendência. */
    List<DocumentoOperacional> listarPreCtePendentes(int idFilial);

    /** Casa um pré-CTe com o CT-e emitido (numero + idConhecimento), limpando a flag pre_cte. */
    void vincularCte(long idDocumento, int numeroCte, long idConhecimentoLegado, String remetente,
                     String destinatario, String cidadeDestino, Instant em);
}
