package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.BiparNfRequest;
import br.com.salome.core.domain.torre.CasamentoResultado;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descarga de coleta: bipa NFs como pré-CTe e casa com o CT-e quando emitido
 * (pela chave da NF). Pré-CTes não casados formam a fila de pendência.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class ColetaService {

    private final AtividadeRepository atividadeRepository;
    private final DocumentoRepository documentoRepository;
    private final ConhecimentoLegadoRepository conhecimentoLegadoRepository;
    private final Clock clock;

    public ColetaService(AtividadeRepository atividadeRepository,
                         DocumentoRepository documentoRepository,
                         ConhecimentoLegadoRepository conhecimentoLegadoRepository,
                         Clock clock) {
        this.atividadeRepository = atividadeRepository;
        this.documentoRepository = documentoRepository;
        this.conhecimentoLegadoRepository = conhecimentoLegadoRepository;
        this.clock = clock;
    }

    public DocumentoOperacional biparNf(long idAtividade, BiparNfRequest req, UsuarioAutenticado usuario) {
        Atividade a = atividadeRepository.buscar(idAtividade, usuario.idFilial())
                .orElseThrow(() -> new RecursoNaoEncontrado("Atividade não encontrada."));
        if (a.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Atividade não está aberta.");
        }
        if (a.tipo() != TipoAtividade.DESCARGA_COLETA) {
            throw new RegraViolada("Bipagem de NF só vale para descarga de coleta.");
        }
        Instant now = clock.instant();
        DocumentoOperacional doc = new DocumentoOperacional(
                null, usuario.idFilial(), null, null, a.idViagemLegado(), true,
                req.volumes(), req.peso(), null, null, null, req.chaveNf(),
                StatusDocumento.NO_ARMAZEM, null, now);
        long docId = documentoRepository.salvar(doc);
        documentoRepository.inserirNf(docId, req.chaveNf(), req.numeroNf(), req.serie(), req.cnpjEmitente());
        documentoRepository.vincularAtividade(idAtividade, docId, "DESCARREGADO", req.volumes(), req.peso(), usuario.id(), now);
        documentoRepository.inserirMovimento(docId, "DESCARGA", idAtividade, idAtividade, null, null, usuario.id(), now);
        return documentoRepository.buscar(docId, usuario.idFilial()).orElseThrow();
    }

    /** Casa os pré-CTes pendentes da filial com os CT-es já emitidos (pela chave da NF). */
    public CasamentoResultado casarPendentes(int idFilial) {
        List<DocumentoOperacional> pendentes = documentoRepository.listarPreCtePendentes(idFilial);
        Instant now = clock.instant();
        int casados = 0;
        for (DocumentoOperacional doc : pendentes) {
            if (doc.chaveNf() == null || doc.chaveNf().isBlank()) {
                continue;
            }
            Optional<CteDescarga> cte = conhecimentoLegadoRepository.buscarCtePorChaveNf(doc.chaveNf());
            if (cte.isPresent() && cte.get().cte() != null) {
                CteDescarga c = cte.get();
                documentoRepository.vincularCte(doc.id(), c.cte(), c.idConhecimento(),
                        c.remetente(), c.destinatario(), c.cidadeDestino(), now);
                casados++;
            }
        }
        return new CasamentoResultado(casados, pendentes.size() - casados);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<DocumentoOperacional> listarPendentes(int idFilial) {
        return documentoRepository.listarPreCtePendentes(idFilial);
    }
}
