package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoFiltro;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoLancamento;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoSnapshot;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoStatus;
import br.com.salome.core.domain.notacompra.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class FluxoCaixaPrevistoServiceTest {

    @Test
    void defaultFilterShouldUseCurrentMonthAndDefaultHorizon() {
        FluxoCaixaPrevistoFiltro filtro = FluxoCaixaPrevistoFiltro.padrao();

        assertEquals(YearMonth.now().atDay(1), filtro.periodoInicio());
        assertEquals(YearMonth.now().atEndOfMonth(), filtro.periodoFim());
        assertEquals(30, filtro.horizonteDias());
        assertEquals(FluxoCaixaPrevistoStatus.TODAS, filtro.status());
    }

    @Test
    void serviceShouldBuildOperationalTimelineWithOpeningBalanceAndForecastSplit() {
        FakeRepository repository = new FakeRepository();
        FluxoCaixaPrevistoService service = new FluxoCaixaPrevistoService(repository);

        FluxoCaixaPrevistoSnapshot snapshot = service.consultar(
                new FluxoCaixaPrevistoFiltro(
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 3),
                        0,
                        null,
                        null,
                        null,
                        null,
                        FluxoCaixaPrevistoStatus.TODAS
                )
        );

        assertEquals(new BigDecimal("1000.00"), snapshot.resumo().saldoInicial());
        assertEquals(3, snapshot.timeline().size());
        assertEquals(new BigDecimal("100.00"), snapshot.timeline().getFirst().saidasPrevistas());
        assertEquals(BigDecimal.ZERO, snapshot.timeline().getFirst().saidasRealizadas());
        assertEquals(new BigDecimal("900.00"), snapshot.timeline().getFirst().saldoProjetado());
        assertEquals(new BigDecimal("700.00"), snapshot.timeline().get(1).saldoProjetado());
        assertEquals(new BigDecimal("800.00"), snapshot.timeline().get(2).saldoRealizado());
        assertEquals(2, snapshot.resumo().quantidadePrevistos());
        assertEquals(1, snapshot.resumo().quantidadeRealizados());
        assertEquals(new BigDecimal("650.00"), snapshot.resumo().saldoFinalProjetado());
        assertEquals(new BigDecimal("800.00"), snapshot.resumo().saldoFinalRealizado());
        assertFalse(snapshot.timeline().getFirst().lancamentos().isEmpty());
        assertTrue(snapshot.timeline().stream().anyMatch(dia -> dia.quantidadeRealizada() > 0));
    }

    @Test
    void repeatedCallsShouldNotCacheResults() {
        FakeRepository repository = new FakeRepository();
        FluxoCaixaPrevistoService service = new FluxoCaixaPrevistoService(repository);

        service.consultar(new FluxoCaixaPrevistoFiltro(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                0,
                null,
                null,
                null,
                null,
                FluxoCaixaPrevistoStatus.TODAS
        ));
        service.consultar(new FluxoCaixaPrevistoFiltro(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                0,
                null,
                null,
                null,
                null,
                FluxoCaixaPrevistoStatus.TODAS
        ));

        assertEquals(2, repository.saldoCalls);
        assertEquals(2, repository.lancamentoCalls);
    }

    @Test
    void manualFiltersShouldPropagateToRepository() {
        FakeRepository repository = new FakeRepository();
        FluxoCaixaPrevistoService service = new FluxoCaixaPrevistoService(repository);

        service.consultar(new FluxoCaixaPrevistoFiltro(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                5,
                "FILIAL A",
                "FORNECEDOR A",
                "BANCO A",
                "PLANO A",
                FluxoCaixaPrevistoStatus.A_VENCER
        ));

        assertEquals("FILIAL A", repository.lastFiltro.filial());
        assertEquals("FORNECEDOR A", repository.lastFiltro.fornecedor());
        assertEquals("BANCO A", repository.lastFiltro.banco());
        assertEquals("PLANO A", repository.lastFiltro.planoContas());
        assertEquals(FluxoCaixaPrevistoStatus.A_VENCER, repository.lastFiltro.status());
        assertEquals(5, repository.lastFiltro.horizonteDias());
    }

    private static final class FakeRepository implements FluxoCaixaPrevistoRepository {
        private final LegacyOrigin origin = LegacyOrigin.of("demo", "forecast test", "fake repo", "notacompraduplicatas");
        private final List<FluxoCaixaPrevistoLancamento> lancamentos = List.of(
                new FluxoCaixaPrevistoLancamento(
                        1,
                        11,
                        "NF-1",
                        "01",
                        1,
                        "FILIAL A",
                        10,
                        "FORNECEDOR A",
                        100,
                        "BANCO A",
                        200,
                        "PLANO A",
                        LocalDate.of(2026, 5, 1),
                        null,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "Vencida",
                        "BOLETO",
                        false,
                        FluxoCaixaPrevistoStatus.A_VENCER,
                        "DocumentoEntradaDetalhesView",
                        origin
                ),
                new FluxoCaixaPrevistoLancamento(
                        2,
                        12,
                        "NF-2",
                        "02",
                        1,
                        "FILIAL A",
                        10,
                        "FORNECEDOR A",
                        100,
                        "BANCO A",
                        200,
                        "PLANO A",
                        LocalDate.of(2026, 5, 2),
                        LocalDate.of(2026, 5, 2),
                        new BigDecimal("200.00"),
                        new BigDecimal("200.00"),
                        "Pago",
                        "PIX",
                        true,
                        FluxoCaixaPrevistoStatus.PAGO,
                        "DocumentoEntradaDetalhesView",
                        origin
                ),
                new FluxoCaixaPrevistoLancamento(
                        3,
                        13,
                        "NF-3",
                        "03",
                        2,
                        "FILIAL B",
                        11,
                        "FORNECEDOR B",
                        101,
                        "BANCO B",
                        201,
                        "PLANO B",
                        LocalDate.of(2026, 5, 3),
                        null,
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        "Aberta",
                        "BOLETO",
                        false,
                        FluxoCaixaPrevistoStatus.EM_ABERTO,
                        "DocumentoEntradaDetalhesView",
                        origin
                )
        );

        private int saldoCalls;
        private int lancamentoCalls;
        private FluxoCaixaPrevistoFiltro lastFiltro;

        @Override
        public BigDecimal consultarSaldoInicial(FluxoCaixaPrevistoFiltro filtro) {
            saldoCalls++;
            lastFiltro = filtro;
            return new BigDecimal("1000.00");
        }

        @Override
        public List<FluxoCaixaPrevistoLancamento> listarLancamentos(FluxoCaixaPrevistoFiltro filtro) {
            lancamentoCalls++;
            lastFiltro = filtro;
            return lancamentos;
        }
    }
}
