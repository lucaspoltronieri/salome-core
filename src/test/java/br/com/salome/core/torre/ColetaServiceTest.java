package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.application.torre.AtividadeRepository;
import br.com.salome.core.application.torre.ColetaService;
import br.com.salome.core.application.torre.ConhecimentoLegadoRepository;
import br.com.salome.core.application.torre.DocumentoRepository;
import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.BiparNfRequest;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ColetaServiceTest {

    private static final int FILIAL = 2;
    private final UsuarioAutenticado operador =
            new UsuarioAutenticado(10L, "João", "joao", FILIAL, PerfilCodigo.OPERADOR);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC);

    private BiparNfRequest nf(long idLocalDestino) {
        return new BiparNfRequest("35260612345678000190550010000000011000000017",
                "1", "1", "12345678000190", 5, BigDecimal.valueOf(80), idLocalDestino);
    }

    @Test
    void coletaSeparacao_ficaNoArmazem() {
        LocalArmazem box = new LocalArmazem(60L, FILIAL, "SEP", "Box Separação", "BOX", true);
        ColetaService service = service(box);

        DocumentoOperacional doc = service.biparNf(1L, nf(60L), operador);

        assertThat(doc.status()).isEqualTo(StatusDocumento.NO_ARMAZEM);
        assertThat(doc.idLocalAtual()).isEqualTo(60L);
        assertThat(doc.preCte()).isTrue();
    }

    @Test
    void coletaTransferencia_ficaSeparado() {
        LocalArmazem box = new LocalArmazem(61L, FILIAL, "TRANSF", "Box Transferência", "BOX", true);
        ColetaService service = service(box);

        DocumentoOperacional doc = service.biparNf(1L, nf(61L), operador);

        assertThat(doc.status()).isEqualTo(StatusDocumento.SEPARADO_BOX);
        assertThat(doc.idLocalAtual()).isEqualTo(61L);
    }

    @Test
    void destinoDistribuicao_invalidoNaColeta() {
        LocalArmazem box = new LocalArmazem(62L, FILIAL, "DIST", "Box Distribuição", "BOX", true);
        ColetaService service = service(box);

        assertThatThrownBy(() -> service.biparNf(1L, nf(62L), operador))
                .isInstanceOf(RegraViolada.class);
    }

    // ---- montagem -------------------------------------------------------

    private ColetaService service(LocalArmazem box) {
        return new ColetaService(atividadeRepo(), documentoRepo(), conhecimentoRepo(), localRepo(box), clock);
    }

    private AtividadeRepository atividadeRepo() {
        Atividade a = new Atividade(1L, FILIAL, TipoAtividade.DESCARGA_COLETA, null,
                StatusAtividade.ABERTA, 888L, "ABC1D23", null, null, clock.instant(), null);
        return new AtividadeRepository() {
            @Override public long inserir(Atividade x) { throw new UnsupportedOperationException(); }
            @Override public Optional<Atividade> buscar(long id, int idFilial) {
                return id == 1L && idFilial == FILIAL ? Optional.of(a) : Optional.empty();
            }
            @Override public List<Atividade> listarAbertas(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public void finalizar(long id, Instant em) { throw new UnsupportedOperationException(); }
            @Override public void cancelar(long id, Instant em, String motivo) { throw new UnsupportedOperationException(); }
            @Override public java.util.Set<Long> idsViagensComDescarga(int idFilial) { throw new UnsupportedOperationException(); }
        };
    }

    private ConhecimentoLegadoRepository conhecimentoRepo() {
        return new ConhecimentoLegadoRepository() {
            @Override public List<CteDescarga> listarCtesDaViagem(long idViagem) { throw new UnsupportedOperationException(); }
            @Override public Optional<CteDescarga> buscarCte(long idConhecimento) { throw new UnsupportedOperationException(); }
            @Override public Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe) { throw new UnsupportedOperationException(); }
        };
    }

    private LocalArmazemRepository localRepo(LocalArmazem box) {
        return new LocalArmazemRepository() {
            @Override public List<LocalArmazem> listarAtivos(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public List<LocalArmazem> listarTodos(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public Optional<LocalArmazem> buscar(long id, int idFilial) {
                return box != null && box.id() == id && box.idFilial() == idFilial ? Optional.of(box) : Optional.empty();
            }
            @Override public long criar(LocalArmazem local) { throw new UnsupportedOperationException(); }
            @Override public boolean definirAtivo(long id, int idFilial, boolean ativo) { throw new UnsupportedOperationException(); }
        };
    }

    private DocumentoRepository documentoRepo() {
        return new DocumentoRepository() {
            private DocumentoOperacional doc;

            @Override public long salvar(DocumentoOperacional d) {
                doc = new DocumentoOperacional(2L, d.idFilial(), d.numeroCte(), d.idConhecimentoLegado(),
                        d.idViagemLegado(), d.preCte(), d.volumes(), d.peso(), d.remetente(), d.destinatario(),
                        d.cidadeDestino(), d.chaveNf(), d.status(), d.idLocalAtual(), d.atualizadoEm());
                return 2L;
            }
            @Override public Optional<DocumentoOperacional> buscar(long id, int idFilial) {
                return doc != null && doc.id() == id ? Optional.of(doc) : Optional.empty();
            }
            @Override public List<DocumentoOperacional> listarPorStatus(int idFilial, List<StatusDocumento> status) { throw new UnsupportedOperationException(); }
            @Override public void atualizarStatusELocal(long id, StatusDocumento status, Long idLocalAtual, Instant em) {
                doc = new DocumentoOperacional(doc.id(), doc.idFilial(), doc.numeroCte(), doc.idConhecimentoLegado(),
                        doc.idViagemLegado(), doc.preCte(), doc.volumes(), doc.peso(), doc.remetente(), doc.destinatario(),
                        doc.cidadeDestino(), doc.chaveNf(), status, idLocalAtual, em);
            }
            @Override public int vincularAtividade(long idAtividade, long idDocumento, String papel,
                                                   Integer volumes, BigDecimal peso, Long idUsuario, Instant em) { return 1; }
            @Override public void inserirMovimento(long idDocumento, String tipo, Long idAtividadeOrigem,
                                                   Long idAtividadeDestino, Long idLocalOrigem, Long idLocalDestino,
                                                   Long idUsuario, Instant em) { }
            @Override public Optional<Long> ultimaAtividadeDescarga(long idDocumento) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPorAtividade(long idAtividade) { throw new UnsupportedOperationException(); }
            @Override public void inserirNf(long idDocumento, String chaveNf, String numeroNf, String serie, String cnpjEmitente) { }
            @Override public List<DocumentoOperacional> listarPreCtePendentes(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public void vincularCte(long idDocumento, int numeroCte, long idConhecimentoLegado, String remetente,
                                              String destinatario, String cidadeDestino, Instant em) { throw new UnsupportedOperationException(); }
        };
    }
}
