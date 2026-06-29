package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
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
 * Separação e carregamento (inclui cross-dock direto). Operam sobre documentos
 * já existentes no armazém da filial.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class MovimentacaoService {

    private final AtividadeRepository atividadeRepository;
    private final DocumentoRepository documentoRepository;
    private final LocalArmazemRepository localRepository;
    private final Clock clock;

    public MovimentacaoService(AtividadeRepository atividadeRepository,
                               DocumentoRepository documentoRepository,
                               LocalArmazemRepository localRepository,
                               Clock clock) {
        this.atividadeRepository = atividadeRepository;
        this.documentoRepository = documentoRepository;
        this.localRepository = localRepository;
        this.clock = clock;
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<DocumentoOperacional> disponiveisParaSeparar(int idFilial) {
        return documentoRepository.listarPorStatus(idFilial, List.of(StatusDocumento.NO_ARMAZEM));
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<DocumentoOperacional> disponiveisParaCarregar(int idFilial) {
        return documentoRepository.listarPorStatus(idFilial,
                List.of(StatusDocumento.NO_ARMAZEM, StatusDocumento.SEPARADO_BOX));
    }

    public DocumentoOperacional separar(long idAtividade, long idDocumento, long idLocal, UsuarioAutenticado usuario) {
        Atividade atividade = exigirAtividade(idAtividade, usuario.idFilial(), TipoAtividade.SEPARACAO);
        DocumentoOperacional doc = exigirDocumento(idDocumento, usuario.idFilial());
        LocalArmazem local = localRepository.buscar(idLocal, usuario.idFilial())
                .orElseThrow(() -> new RecursoNaoEncontrado("Local não encontrado."));
        if (doc.status() != StatusDocumento.NO_ARMAZEM && doc.status() != StatusDocumento.EM_SEPARACAO) {
            throw new RegraViolada("Documento não está disponível para separação (status " + doc.status() + ").");
        }
        var now = clock.instant();
        documentoRepository.vincularAtividade(idAtividade, idDocumento, "SEPARADO", doc.volumes(), doc.peso(), usuario.id(), now);
        documentoRepository.atualizarStatusELocal(idDocumento, StatusDocumento.SEPARADO_BOX, local.id(), now);
        documentoRepository.inserirMovimento(idDocumento, "SEPARACAO", idAtividade, idAtividade,
                doc.idLocalAtual(), local.id(), usuario.id(), now);
        return documentoRepository.buscar(idDocumento, usuario.idFilial()).orElseThrow();
    }

    /** Modo rápido: separa vários documentos para o mesmo box de uma vez. */
    public List<DocumentoOperacional> separarLote(long idAtividade, List<Long> idsDocumento, long idLocal, UsuarioAutenticado usuario) {
        return idsDocumento.stream()
                .map(idDocumento -> separar(idAtividade, idDocumento, idLocal, usuario))
                .toList();
    }

    public DocumentoOperacional carregar(long idAtividade, long idDocumento, UsuarioAutenticado usuario) {
        Atividade atividade = exigirAtividade(idAtividade, usuario.idFilial(), TipoAtividade.CARREGAMENTO);
        DocumentoOperacional doc = exigirDocumento(idDocumento, usuario.idFilial());
        if (doc.status() != StatusDocumento.NO_ARMAZEM && doc.status() != StatusDocumento.SEPARADO_BOX) {
            throw new RegraViolada("Documento não está disponível para carregamento (status " + doc.status() + ").");
        }
        // Cross-dock direto: documento carregado sem ter passado por separação.
        boolean crossDock = doc.status() == StatusDocumento.NO_ARMAZEM;
        Long origem = crossDock ? documentoRepository.ultimaAtividadeDescarga(idDocumento).orElse(null) : null;

        var now = clock.instant();
        // Marcar = "em carregamento". O status final (CARREGADO) só vem na conclusão do carregamento.
        documentoRepository.vincularAtividade(idAtividade, idDocumento, "CARREGADO", doc.volumes(), doc.peso(), usuario.id(), now);
        documentoRepository.atualizarStatusELocal(idDocumento, StatusDocumento.EM_CARREGAMENTO, doc.idLocalAtual(), now);
        documentoRepository.inserirMovimento(idDocumento, crossDock ? "CROSS_DOCK" : "CARREGAMENTO",
                origem, idAtividade, doc.idLocalAtual(), null, usuario.id(), now);
        return documentoRepository.buscar(idDocumento, usuario.idFilial()).orElseThrow();
    }

    /** Modo rápido: marca vários documentos para carregamento de uma vez. */
    public List<DocumentoOperacional> carregarLote(long idAtividade, List<Long> idsDocumento, UsuarioAutenticado usuario) {
        return idsDocumento.stream()
                .map(idDocumento -> carregar(idAtividade, idDocumento, usuario))
                .toList();
    }

    /** Conclui o carregamento: documentos EM_CARREGAMENTO desta atividade viram CARREGADO. */
    public int concluirCarregamento(long idAtividade, UsuarioAutenticado usuario) {
        exigirAtividade(idAtividade, usuario.idFilial(), TipoAtividade.CARREGAMENTO);
        var now = clock.instant();
        int efetivados = 0;
        for (DocumentoOperacional doc : documentoRepository.listarPorAtividade(idAtividade)) {
            if (doc.status() != StatusDocumento.EM_CARREGAMENTO) {
                continue;
            }
            documentoRepository.atualizarStatusELocal(doc.id(), StatusDocumento.CARREGADO, doc.idLocalAtual(), now);
            efetivados++;
        }
        return efetivados;
    }

    private Atividade exigirAtividade(long idAtividade, int idFilial, TipoAtividade tipoEsperado) {
        Atividade a = atividadeRepository.buscar(idAtividade, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Atividade não encontrada."));
        if (a.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Atividade não está aberta.");
        }
        if (a.tipo() != tipoEsperado) {
            throw new RegraViolada("Atividade não é do tipo " + tipoEsperado + ".");
        }
        return a;
    }

    private DocumentoOperacional exigirDocumento(long idDocumento, int idFilial) {
        return documentoRepository.buscar(idDocumento, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Documento não encontrado."));
    }
}
