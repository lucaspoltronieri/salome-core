package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.application.torre.AtividadeRepository;
import br.com.salome.core.application.torre.ConhecimentoLegadoRepository;
import br.com.salome.core.application.torre.DocumentoRepository;
import br.com.salome.core.application.torre.DocumentoService;
import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DocumentoServiceTest {

    private static final int FILIAL = 2;
    private final UsuarioAutenticado operador =
            new UsuarioAutenticado(10L, "João", "joao", FILIAL, PerfilCodigo.OPERADOR);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC);

    private final AtomicReference<Long> movDestino = new AtomicReference<>();

    @Test
    void descargaSeparacao_ficaEmDescargaNoBoxSepAteConcluir() {
        LocalArmazem box = new LocalArmazem(50L, FILIAL, "SEP", "Box Separação", "BOX", true);
        DocumentoService service = service(atividadeAberta(), box, cteValido());

        DocumentoOperacional doc = service.registrarDescarga(1L, 777L, 50L, operador);

        assertThat(doc.status()).isEqualTo(StatusDocumento.EM_DESCARGA);
        assertThat(doc.idLocalAtual()).isEqualTo(50L);
        assertThat(movDestino.get()).isEqualTo(50L);
    }

    @Test
    void descargaDistribuicao_ficaEmDescargaNoBoxDistAteConcluir() {
        LocalArmazem box = new LocalArmazem(51L, FILIAL, "DIST", "Box Distribuição", "BOX", true);
        DocumentoService service = service(atividadeAberta(), box, cteValido());

        DocumentoOperacional doc = service.registrarDescarga(1L, 777L, 51L, operador);

        assertThat(doc.status()).isEqualTo(StatusDocumento.EM_DESCARGA);
        assertThat(doc.idLocalAtual()).isEqualTo(51L);
    }

    @Test
    void concluirDescarga_bloqueiaQuandoExisteCteDaViagemSemMarcacao() {
        CteDescarga marcado = cte(777L, 12345);
        CteDescarga pendente = cte(778L, 12346);
        DocumentoOperacional docMarcado = documento(1L, marcado.idConhecimento(), StatusDocumento.EM_DESCARGA, 50L);
        DocumentoService service = service(
                atividadeAberta(),
                List.of(new LocalArmazem(50L, FILIAL, "SEP", "Box Separação", "BOX", true)),
                marcado,
                List.of(marcado, pendente),
                documentoRepo(docMarcado));

        assertThatThrownBy(() -> service.concluirDescarga(1L, operador))
                .isInstanceOf(RegraViolada.class)
                .hasMessageContaining("1 CT-e(s) da viagem ainda sem destino")
                .hasMessageContaining("12346");
    }

    @Test
    void concluirDescarga_quandoTodosCtesMarcadosEfetivaStatusFinal() {
        CteDescarga cteSep = cte(777L, 12345);
        CteDescarga cteDist = cte(778L, 12346);
        DocumentoRepository documentos = documentoRepo(
                documento(1L, cteSep.idConhecimento(), StatusDocumento.EM_DESCARGA, 50L),
                documento(2L, cteDist.idConhecimento(), StatusDocumento.EM_DESCARGA, 51L));
        DocumentoService service = service(
                atividadeAberta(),
                List.of(
                        new LocalArmazem(50L, FILIAL, "SEP", "Box Separação", "BOX", true),
                        new LocalArmazem(51L, FILIAL, "DIST", "Box Distribuição", "BOX", true)),
                cteSep,
                List.of(cteSep, cteDist),
                documentos);

        int efetivados = service.concluirDescarga(1L, operador);

        assertThat(efetivados).isEqualTo(2);
        assertThat(documentos.listarPorAtividade(1L))
                .extracting(DocumentoOperacional::status)
                .containsExactly(StatusDocumento.NO_ARMAZEM, StatusDocumento.SEPARADO_BOX);
    }

    @Test
    void destinoTransferencia_invalidoNaDescargaDeTransferencia() {
        LocalArmazem box = new LocalArmazem(52L, FILIAL, "TRANSF", "Box Transferência", "BOX", true);
        DocumentoService service = service(atividadeAberta(), box, cteValido());

        assertThatThrownBy(() -> service.registrarDescarga(1L, 777L, 52L, operador))
                .isInstanceOf(RegraViolada.class);
    }

    @Test
    void boxInexistente_lancaRecursoNaoEncontrado() {
        DocumentoService service = service(atividadeAberta(), null, cteValido());

        assertThatThrownBy(() -> service.registrarDescarga(1L, 777L, 99L, operador))
                .isInstanceOf(RecursoNaoEncontrado.class);
    }

    // ---- montagem -------------------------------------------------------

    private DocumentoService service(Atividade atividade, LocalArmazem box, CteDescarga cte) {
        return service(atividade, box == null ? List.of() : List.of(box), cte,
                cte == null ? List.of() : List.of(cte), documentoRepo());
    }

    private DocumentoService service(Atividade atividade, List<LocalArmazem> boxes, CteDescarga cte,
                                     List<CteDescarga> ctesViagem, DocumentoRepository documentos) {
        return new DocumentoService(
                atividadeRepo(atividade), conhecimentoRepo(cte, ctesViagem), documentos, localRepo(boxes), clock);
    }

    private Atividade atividadeAberta() {
        return new Atividade(1L, FILIAL, TipoAtividade.DESCARGA_TRANSFERENCIA, null,
                StatusAtividade.ABERTA, 888L, "ABC1D23", null, null, clock.instant(), null);
    }

    private CteDescarga cteValido() {
        return cte(777L, 12345);
    }

    private CteDescarga cte(long idConhecimento, Integer numero) {
        return new CteDescarga(idConhecimento, numero, "NF1", BigDecimal.valueOf(3), BigDecimal.valueOf(120.5),
                "Remetente", "Destinatário", "São Paulo");
    }

    private DocumentoOperacional documento(long id, long idConhecimento, StatusDocumento status, long idLocal) {
        return new DocumentoOperacional(id, FILIAL, (int) idConhecimento, idConhecimento, 888L,
                false, 3, BigDecimal.valueOf(120.5), "Remetente", "Destinatário", "São Paulo",
                null, status, idLocal, clock.instant());
    }

    private AtividadeRepository atividadeRepo(Atividade atividade) {
        return new AtividadeRepository() {
            @Override public long inserir(Atividade a) { throw new UnsupportedOperationException(); }
            @Override public Optional<Atividade> buscar(long id, int idFilial) {
                return atividade != null && atividade.id() == id && atividade.idFilial() == idFilial
                        ? Optional.of(atividade) : Optional.empty();
            }
            @Override public List<Atividade> listarAbertas(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public void finalizar(long id, Instant em) { throw new UnsupportedOperationException(); }
            @Override public void cancelar(long id, Instant em, String motivo) { throw new UnsupportedOperationException(); }
            @Override public java.util.Set<Long> idsViagensComDescarga(int idFilial) { throw new UnsupportedOperationException(); }
        };
    }

    private ConhecimentoLegadoRepository conhecimentoRepo(CteDescarga cte, List<CteDescarga> ctesViagem) {
        return new ConhecimentoLegadoRepository() {
            @Override public List<CteDescarga> listarCtesDaViagem(long idViagem) { return ctesViagem; }
            @Override public Optional<CteDescarga> buscarCte(long idConhecimento) {
                return cte != null && cte.idConhecimento() == idConhecimento ? Optional.of(cte) : Optional.empty();
            }
            @Override public Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe) { throw new UnsupportedOperationException(); }
        };
    }

    private LocalArmazemRepository localRepo(List<LocalArmazem> boxes) {
        return new LocalArmazemRepository() {
            @Override public List<LocalArmazem> listarAtivos(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public List<LocalArmazem> listarTodos(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public Optional<LocalArmazem> buscar(long id, int idFilial) {
                return boxes.stream()
                        .filter(box -> box.id() == id && box.idFilial() == idFilial)
                        .findFirst();
            }
            @Override public long criar(LocalArmazem local) { throw new UnsupportedOperationException(); }
            @Override public boolean definirAtivo(long id, int idFilial, boolean ativo) { throw new UnsupportedOperationException(); }
        };
    }

    /** Fake stateful: guarda um documento e aplica salvar/atualizarStatusELocal; captura o destino do movimento. */
    private DocumentoRepository documentoRepo(DocumentoOperacional... iniciais) {
        return new DocumentoRepository() {
            private final List<DocumentoOperacional> docs = new ArrayList<>(Arrays.asList(iniciais));

            @Override public long salvar(DocumentoOperacional d) {
                DocumentoOperacional doc = new DocumentoOperacional(1L, d.idFilial(), d.numeroCte(), d.idConhecimentoLegado(),
                        d.idViagemLegado(), d.preCte(), d.volumes(), d.peso(), d.remetente(), d.destinatario(),
                        d.cidadeDestino(), d.chaveNf(), d.status(), d.idLocalAtual(), d.atualizadoEm());
                docs.clear();
                docs.add(doc);
                return 1L;
            }
            @Override public Optional<DocumentoOperacional> buscar(long id, int idFilial) {
                return docs.stream().filter(doc -> doc.id() == id).findFirst();
            }
            @Override public List<DocumentoOperacional> listarPorStatus(int idFilial, List<StatusDocumento> status) { throw new UnsupportedOperationException(); }
            @Override public void atualizarStatusELocal(long id, StatusDocumento status, Long idLocalAtual, Instant em) {
                for (int i = 0; i < docs.size(); i++) {
                    DocumentoOperacional doc = docs.get(i);
                    if (doc.id() == id) {
                        docs.set(i, new DocumentoOperacional(doc.id(), doc.idFilial(), doc.numeroCte(),
                                doc.idConhecimentoLegado(), doc.idViagemLegado(), doc.preCte(), doc.volumes(),
                                doc.peso(), doc.remetente(), doc.destinatario(), doc.cidadeDestino(),
                                doc.chaveNf(), status, idLocalAtual, em));
                    }
                }
            }
            @Override public int vincularAtividade(long idAtividade, long idDocumento, String papel,
                                                   Integer volumes, BigDecimal peso, Long idUsuario, Instant em) { return 1; }
            @Override public void inserirMovimento(long idDocumento, String tipo, Long idAtividadeOrigem,
                                                   Long idAtividadeDestino, Long idLocalOrigem, Long idLocalDestino,
                                                   Long idUsuario, Instant em) { movDestino.set(idLocalDestino); }
            @Override public Optional<Long> ultimaAtividadeDescarga(long idDocumento) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPorAtividade(long idAtividade) { return List.copyOf(docs); }
            @Override public void inserirNf(long idDocumento, String chaveNf, String numeroNf, String serie, String cnpjEmitente) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPreCtePendentes(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public void vincularCte(long idDocumento, int numeroCte, long idConhecimentoLegado, String remetente,
                                              String destinatario, String cidadeDestino, Instant em) { throw new UnsupportedOperationException(); }
        };
    }
}
