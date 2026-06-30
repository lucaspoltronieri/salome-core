package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.salome.core.application.torre.AtividadeRepository;
import br.com.salome.core.application.torre.FilialTorreRepository;
import br.com.salome.core.application.torre.ViagemAguardandoService;
import br.com.salome.core.application.torre.ViagemLegadoRepository;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ViagemAguardandoServiceTest {

    private static final int FILIAL = 2;
    private static final String DATA_INICIO = "2026-07-01";
    // "Hoje" deliberadamente depois do piso, p/ provar qual corte cada método usa.
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private final Clock clock =
            Clock.fixed(LocalDate.of(2026, 7, 15).atStartOfDay(ZONA).toInstant(), ZONA);

    @Test
    void listar_transferencias_usaPisoFixoNaoOHoje() {
        AtomicReference<LocalDate> corteUsado = new AtomicReference<>();
        ViagemLegadoRepository legado = legado(corteUsado, new AtomicReference<>(),
                List.of(viagem(10L, 100L)), List.of());

        ViagemAguardandoService service = new ViagemAguardandoService(
                legado, filialAtiva(), atividade(Set.of()), clock, DATA_INICIO);

        List<ViagemAguardando> resultado = service.listar(FILIAL);

        assertThat(corteUsado.get()).isEqualTo(LocalDate.parse(DATA_INICIO));
        assertThat(corteUsado.get()).isNotEqualTo(LocalDate.now(clock));
        assertThat(resultado).hasSize(1);
    }

    @Test
    void listar_excluiViagensComDescargaRegistrada() {
        ViagemLegadoRepository legado = legado(new AtomicReference<>(), new AtomicReference<>(),
                List.of(viagem(10L, 100L), viagem(11L, 200L)), List.of());

        // Viagem 100 já tem descarga aberta na Torre -> some da lista.
        ViagemAguardandoService service = new ViagemAguardandoService(
                legado, filialAtiva(), atividade(Set.of(100L)), clock, DATA_INICIO);

        List<ViagemAguardando> resultado = service.listar(FILIAL);

        assertThat(resultado).extracting(ViagemAguardando::idViagem).containsExactly(200L);
    }

    @Test
    void listarColetas_usaHojeComoCorte() {
        AtomicReference<LocalDate> corteColetas = new AtomicReference<>();
        ViagemLegadoRepository legado = legado(new AtomicReference<>(), corteColetas,
                List.of(), List.of(viagem(20L, 300L)));

        ViagemAguardandoService service = new ViagemAguardandoService(
                legado, filialAtiva(), atividade(Set.of()), clock, DATA_INICIO);

        List<ViagemAguardando> resultado = service.listarColetas(FILIAL);

        assertThat(corteColetas.get()).isEqualTo(LocalDate.now(clock));
        assertThat(corteColetas.get()).isNotEqualTo(LocalDate.parse(DATA_INICIO));
        assertThat(resultado).hasSize(1);
    }

    // ---- stubs ----------------------------------------------------------

    private static ViagemAguardando viagem(long idTransf, long idViagem) {
        return new ViagemAguardando(idTransf, idViagem, LocalDate.of(2026, 7, 10), "08:00",
                "ABC1D23", "Motorista", "Origem", 1, BigDecimal.ONE, BigDecimal.TEN);
    }

    private ViagemLegadoRepository legado(AtomicReference<LocalDate> corteTransf,
                                          AtomicReference<LocalDate> corteColetas,
                                          List<ViagemAguardando> transferencias,
                                          List<ViagemAguardando> coletas) {
        return new ViagemLegadoRepository() {
            @Override
            public List<ViagemAguardando> listarAguardandoDescarga(int idFilialDestino, LocalDate dataCorte, int limite) {
                corteTransf.set(dataCorte);
                return transferencias;
            }

            @Override
            public List<ViagemAguardando> listarColetasAguardando(int idFilial, LocalDate dataCorte, int limite) {
                corteColetas.set(dataCorte);
                return coletas;
            }
        };
    }

    private FilialTorreRepository filialAtiva() {
        return new FilialTorreRepository() {
            @Override
            public Optional<FilialTorre> buscar(int idFilial) {
                return Optional.of(new FilialTorre(FILIAL, "Rio Preto", LocalDate.of(2026, 6, 1), true));
            }

            @Override
            public List<FilialTorre> listarAtivas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FilialTorre> listarTodas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void salvar(FilialTorre filial) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AtividadeRepository atividade(Set<Long> comDescarga) {
        return new AtividadeRepository() {
            @Override
            public Set<Long> idsViagensComDescarga(int idFilial) {
                return comDescarga;
            }

            @Override
            public long inserir(Atividade atividade) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Atividade> buscar(long id, int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Atividade> listarAbertas(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void finalizar(long id, Instant finalizadaEm) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void cancelar(long id, Instant canceladaEm, String motivo) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
