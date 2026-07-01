package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.application.torre.AtividadeRepository;
import br.com.salome.core.application.torre.AtividadeService;
import br.com.salome.core.application.torre.MovimentacaoService;
import br.com.salome.core.domain.torre.AbrirAtividadeRequest;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.CaminhaoEmDescarga;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regra "separa-se a viagem uma vez": viagem com separação concluída some da lista
 * de caminhões; abrir separação de uma viagem já separada é bloqueado.
 */
class SeparacaoUnicaTest {

    private static final int FILIAL = 2;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), java.time.ZoneOffset.UTC);

    @Test
    void caminhoesParaSeparar_escondeViagemJaSeparada() {
        var repo = new FakeAtividadeRepo() {
            @Override public List<CaminhaoEmDescarga> listarCaminhoesEmDescarga(int idFilial, Instant desde) {
                return List.of(
                        new CaminhaoEmDescarga(100L, "AAA1A11", true),
                        new CaminhaoEmDescarga(200L, "BBB2B22", false));
            }
            @Override public Set<Long> idsViagensComSeparacaoConcluida(int idFilial) {
                return Set.of(200L); // viagem 200 já separada uma vez
            }
        };
        var service = new MovimentacaoService(repo, null, null, clock);

        var caminhoes = service.caminhoesParaSeparar(FILIAL);

        assertThat(caminhoes).extracting(CaminhaoEmDescarga::idViagem).containsExactly(100L);
    }

    @Test
    void abrir_separacaoDeViagemJaSeparada_bloqueia() {
        var repo = new FakeAtividadeRepo() {
            @Override public Set<Long> idsViagensComSeparacaoConcluida(int idFilial) {
                return Set.of(100L);
            }
        };
        var service = new AtividadeService(repo, null, null, null, null, null, null, null, clock);
        var request = new AbrirAtividadeRequest(TipoAtividade.SEPARACAO, null, 100L, "AAA1A11", null);
        var usuario = new UsuarioAutenticado(7L, "Operador", "op", FILIAL, PerfilCodigo.OPERADOR);

        assertThatThrownBy(() -> service.abrir(request, usuario))
                .isInstanceOf(RegraViolada.class)
                .hasMessageContaining("já foi separada");
    }

    /** Base de fake: métodos não usados nestes testes lançam exceção. */
    private static class FakeAtividadeRepo implements AtividadeRepository {
        @Override public long inserir(Atividade atividade) { throw new UnsupportedOperationException(); }
        @Override public Optional<Atividade> buscar(long id, int idFilial) { throw new UnsupportedOperationException(); }
        @Override public List<Atividade> listarAbertas(int idFilial) { throw new UnsupportedOperationException(); }
        @Override public void finalizar(long id, Instant finalizadaEm) { throw new UnsupportedOperationException(); }
        @Override public void cancelar(long id, Instant canceladaEm, String motivo) { throw new UnsupportedOperationException(); }
        @Override public Set<Long> idsViagensComDescarga(int idFilial) { throw new UnsupportedOperationException(); }
    }
}
