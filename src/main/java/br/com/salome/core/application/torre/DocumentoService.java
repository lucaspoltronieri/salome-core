package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
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
    private final Clock clock;

    public DocumentoService(AtividadeRepository atividadeRepository,
                            ConhecimentoLegadoRepository conhecimentoLegadoRepository,
                            DocumentoRepository documentoRepository,
                            Clock clock) {
        this.atividadeRepository = atividadeRepository;
        this.conhecimentoLegadoRepository = conhecimentoLegadoRepository;
        this.documentoRepository = documentoRepository;
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

    public DocumentoOperacional registrarDescarga(long idAtividade, long idConhecimento, UsuarioAutenticado usuario) {
        Atividade a = exigir(idAtividade, usuario.idFilial());
        if (a.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Atividade não está aberta.");
        }
        if (a.tipo() != TipoAtividade.DESCARGA_TRANSFERENCIA) {
            throw new RegraViolada("Registro de descarga só vale para descarga de transferência.");
        }
        CteDescarga cte = conhecimentoLegadoRepository.buscarCte(idConhecimento)
                .orElseThrow(() -> new RecursoNaoEncontrado("CT-e não encontrado no legado."));

        Integer volumes = cte.volumes() == null ? null : cte.volumes().intValue();
        var now = clock.instant();
        DocumentoOperacional doc = new DocumentoOperacional(
                null, usuario.idFilial(), cte.cte(), cte.idConhecimento(), a.idViagemLegado(),
                false, volumes, cte.peso(), cte.remetente(), cte.destinatario(), cte.cidadeDestino(),
                null, StatusDocumento.NO_ARMAZEM, null, now);

        long docId = documentoRepository.salvar(doc);
        int vinculado = documentoRepository.vincularAtividade(
                idAtividade, docId, "DESCARREGADO", volumes, cte.peso(), usuario.id(), now);
        // Só registra movimento se o vínculo é novo (evita movimento duplicado em re-bipagem).
        if (vinculado > 0) {
            documentoRepository.inserirMovimento(docId, "DESCARGA", idAtividade, idAtividade, null, null, usuario.id(), now);
        }

        return new DocumentoOperacional(docId, doc.idFilial(), doc.numeroCte(), doc.idConhecimentoLegado(),
                doc.idViagemLegado(), false, volumes, cte.peso(), cte.remetente(), cte.destinatario(),
                cte.cidadeDestino(), null, StatusDocumento.NO_ARMAZEM, null, now);
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
