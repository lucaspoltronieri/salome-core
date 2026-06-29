package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.BoxPadrao;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos (CT-es) no fluxo de descarga de transferência: lista os CT-es da
 * viagem e registra o CT-e como descarregado (documento + vínculo + movimento).
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class DocumentoService {

    private final AtividadeRepository atividadeRepository;
    private final ConhecimentoLegadoRepository conhecimentoLegadoRepository;
    private final DocumentoRepository documentoRepository;
    private final LocalArmazemRepository localRepository;
    private final Clock clock;

    public DocumentoService(AtividadeRepository atividadeRepository,
                            ConhecimentoLegadoRepository conhecimentoLegadoRepository,
                            DocumentoRepository documentoRepository,
                            LocalArmazemRepository localRepository,
                            Clock clock) {
        this.atividadeRepository = atividadeRepository;
        this.conhecimentoLegadoRepository = conhecimentoLegadoRepository;
        this.documentoRepository = documentoRepository;
        this.localRepository = localRepository;
        this.clock = clock;
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<CteDescarga> listarCtesDaDescarga(long idAtividade, UsuarioAutenticado usuario) {
        Atividade a = exigir(idAtividade, usuario.idFilial());
        if (a.idViagemLegado() == null) {
            throw new RegraViolada("Atividade sem viagem associada.");
        }
        return conhecimentoLegadoRepository.listarCtesDaViagem(a.idViagemLegado());
    }

    public DocumentoOperacional registrarDescarga(long idAtividade, long idConhecimento,
                                                  long idLocalDestino, UsuarioAutenticado usuario) {
        Atividade a = exigir(idAtividade, usuario.idFilial());
        if (a.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Atividade não está aberta.");
        }
        if (a.tipo() != TipoAtividade.DESCARGA_TRANSFERENCIA) {
            throw new RegraViolada("Registro de descarga só vale para descarga de transferência.");
        }
        LocalArmazem box = localRepository.buscar(idLocalDestino, usuario.idFilial())
                .orElseThrow(() -> new RecursoNaoEncontrado("Box destino não encontrado."));
        BoxPadrao destino = BoxPadrao.porCodigo(box.codigo())
                .filter(BoxPadrao.DESTINOS_TRANSFERENCIA::contains)
                .orElseThrow(() -> new RegraViolada(
                        "Destino inválido para descarga de transferência: use Distribuição ou Separação."));
        CteDescarga cte = conhecimentoLegadoRepository.buscarCte(idConhecimento)
                .orElseThrow(() -> new RecursoNaoEncontrado("CT-e não encontrado no legado."));

        Integer volumes = cte.volumes() == null ? null : cte.volumes().intValue();
        var now = clock.instant();
        // Marcar = "sendo descarregado" (EM_DESCARGA). O box destino fica guardado em
        // id_local_atual; só na conclusão da descarga o status vira final (NO_ARMAZEM/SEPARADO_BOX).
        DocumentoOperacional doc = new DocumentoOperacional(
                null, usuario.idFilial(), cte.cte(), cte.idConhecimento(), a.idViagemLegado(),
                false, volumes, cte.peso(), cte.remetente(), cte.destinatario(), cte.cidadeDestino(),
                null, StatusDocumento.EM_DESCARGA, box.id(), now);

        long docId = documentoRepository.salvar(doc);
        int vinculado = documentoRepository.vincularAtividade(
                idAtividade, docId, "DESCARREGADO", volumes, cte.peso(), usuario.id(), now);
        // Só define destino/movimento na 1ª bipagem desta atividade (re-bipar é idempotente,
        // não altera o destino escolhido antes).
        if (vinculado > 0) {
            documentoRepository.atualizarStatusELocal(docId, StatusDocumento.EM_DESCARGA, box.id(), now);
            documentoRepository.inserirMovimento(docId, "DESCARGA", idAtividade, idAtividade, null, box.id(), usuario.id(), now);
        }

        return documentoRepository.buscar(docId, usuario.idFilial()).orElseThrow();
    }

    /** Modo rápido: marca vários CT-es de uma vez para o mesmo box destino. */
    public List<DocumentoOperacional> registrarDescargaLote(long idAtividade, List<Long> idsConhecimento,
                                                            long idLocalDestino, UsuarioAutenticado usuario) {
        return idsConhecimento.stream()
                .map(idConhecimento -> registrarDescarga(idAtividade, idConhecimento, idLocalDestino, usuario))
                .toList();
    }

    /**
     * Conclui a descarga: cada documento ainda EM_DESCARGA desta atividade vira o status final
     * conforme o box destino já escolhido (Separação → NO_ARMAZEM; Distribuição/Transferência →
     * SEPARADO_BOX). Idempotente. Retorna quantos foram efetivados.
     */
    public int concluirDescarga(long idAtividade, UsuarioAutenticado usuario) {
        exigir(idAtividade, usuario.idFilial());
        var now = clock.instant();
        int efetivados = 0;
        for (DocumentoOperacional doc : documentoRepository.listarPorAtividade(idAtividade)) {
            if (doc.status() != StatusDocumento.EM_DESCARGA || doc.idLocalAtual() == null) {
                continue;
            }
            StatusDocumento finalStatus = statusFinalDoBox(doc.idLocalAtual(), usuario.idFilial());
            documentoRepository.atualizarStatusELocal(doc.id(), finalStatus, doc.idLocalAtual(), now);
            documentoRepository.inserirMovimento(doc.id(), "ARMAZENAGEM", idAtividade, idAtividade,
                    doc.idLocalAtual(), doc.idLocalAtual(), usuario.id(), now);
            efetivados++;
        }
        return efetivados;
    }

    /** Mapeia o box destino (SEP/DIST/TRANSF) para o status final do documento. */
    private StatusDocumento statusFinalDoBox(long idLocal, int idFilial) {
        return localRepository.buscar(idLocal, idFilial)
                .flatMap(l -> BoxPadrao.porCodigo(l.codigo()))
                .map(BoxPadrao::statusAposDescarga)
                .orElse(StatusDocumento.NO_ARMAZEM);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<DocumentoOperacional> listarDocumentos(long idAtividade, UsuarioAutenticado usuario) {
        exigir(idAtividade, usuario.idFilial());
        return documentoRepository.listarPorAtividade(idAtividade);
    }

    private Atividade exigir(long idAtividade, int idFilial) {
        return atividadeRepository.buscar(idAtividade, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Atividade não encontrada."));
    }
}
