package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.salome.core.application.torre.ArmazemService;
import br.com.salome.core.application.torre.ConhecimentoLegadoRepository;
import br.com.salome.core.application.torre.DocumentoRepository;
import br.com.salome.core.domain.torre.ConhecimentoDatas;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoArmazenado;
import br.com.salome.core.domain.torre.DocumentoComLocal;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoVeiculo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArmazemServiceTest {

    private static final int FILIAL = 2;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void snapshot_enriqueceDatasDoLegadoEPreservaChegadaDaColeta() {
        DocumentoArmazenado transferencia = documento(1L, 777L, null);
        DocumentoArmazenado coleta = documento(2L, 778L, LocalDate.of(2026, 6, 27));
        ArmazemService service = new ArmazemService(
                documentoRepo(List.of(transferencia, coleta)),
                conhecimentoRepo(),
                clock);

        List<DocumentoArmazenado> docs = service.snapshot(FILIAL).documentos();

        assertThat(docs).extracting(DocumentoArmazenado::dataEmissao)
                .containsExactly(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 26));
        assertThat(docs).extracting(DocumentoArmazenado::dataChegada)
                .containsExactly(LocalDate.of(2026, 6, 28), LocalDate.of(2026, 6, 27));
        assertThat(docs).extracting(DocumentoArmazenado::dataPrevistaEntrega)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));
    }

    private DocumentoArmazenado documento(long id, long idConhecimento, LocalDate dataChegadaColeta) {
        return new DocumentoArmazenado(id, (int) idConhecimento, false, 3, BigDecimal.valueOf(120.5),
                "Remetente", "Destinatario", "Sao Paulo", null, dataChegadaColeta, null,
                StatusDocumento.NO_ARMAZEM, 50L, "SEP", "Box Separacao", "BOX",
                idConhecimento, clock.instant());
    }

    private ConhecimentoLegadoRepository conhecimentoRepo() {
        return new ConhecimentoLegadoRepository() {
            @Override public List<CteDescarga> listarCtesDaViagem(long idViagem) { throw new UnsupportedOperationException(); }
            @Override public Optional<CteDescarga> buscarCte(long idConhecimento) { throw new UnsupportedOperationException(); }
            @Override public Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe) { throw new UnsupportedOperationException(); }
            @Override public Map<Long, ConhecimentoDatas> datasPorConhecimento(Collection<Long> idsConhecimento, int idFilial) {
                assertThat(idFilial).isEqualTo(FILIAL);
                return Map.of(
                        777L, new ConhecimentoDatas(
                                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 28), LocalDate.of(2026, 7, 1)),
                        778L, new ConhecimentoDatas(
                                LocalDate.of(2026, 6, 26), LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 2)));
            }
        };
    }

    private DocumentoRepository documentoRepo(List<DocumentoArmazenado> documentos) {
        return new DocumentoRepository() {
            @Override public long salvar(DocumentoOperacional documento) { throw new UnsupportedOperationException(); }
            @Override public Optional<DocumentoOperacional> buscar(long id, int idFilial) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPorStatus(int idFilial, List<StatusDocumento> status) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoArmazenado> listarArmazenados(int idFilial) {
                assertThat(idFilial).isEqualTo(FILIAL);
                return documentos;
            }
            @Override public List<DocumentoComLocal> listarParaCarregar(int idFilial, TipoVeiculo tipo) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoComLocal> listarSeparaveisDaViagem(int idFilial, long idViagem) { throw new UnsupportedOperationException(); }
            @Override public void atualizarStatusELocal(long id, StatusDocumento status, Long idLocalAtual, Instant em) { throw new UnsupportedOperationException(); }
            @Override public int vincularAtividade(long idAtividade, long idDocumento, String papel,
                                                   Integer volumes, BigDecimal peso, Long idUsuario, Instant em) { throw new UnsupportedOperationException(); }
            @Override public void inserirMovimento(long idDocumento, String tipo, Long idAtividadeOrigem,
                                                   Long idAtividadeDestino, Long idLocalOrigem, Long idLocalDestino,
                                                   Long idUsuario, Instant em) { throw new UnsupportedOperationException(); }
            @Override public Optional<Long> ultimaAtividadeDescarga(long idDocumento) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPorAtividade(long idAtividade) { throw new UnsupportedOperationException(); }
            @Override public void inserirNf(long idDocumento, String chaveNf, String numeroNf, String serie, String cnpjEmitente) { throw new UnsupportedOperationException(); }
            @Override public List<DocumentoOperacional> listarPreCtePendentes(int idFilial) { throw new UnsupportedOperationException(); }
            @Override public void vincularCte(long idDocumento, int numeroCte, long idConhecimentoLegado,
                                              String remetente, String destinatario, String cidadeDestino, Instant em) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
