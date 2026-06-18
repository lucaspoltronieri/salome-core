package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.DreClienteDetalhe;
import br.com.salome.core.domain.financeiro.DreClienteDriver;
import br.com.salome.core.domain.financeiro.DreClienteLinha;
import br.com.salome.core.domain.financeiro.DreClienteSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreClienteServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneId.of("UTC"));

    private static final List<PlanoConta> PLANO = List.of(
            new PlanoConta("1", "RECEITAS", true, false),
            new PlanoConta("1.01", "RECEITA OPERACIONAL", true, false),
            new PlanoConta("1.01.001", "Receita de fretes", false, false),
            new PlanoConta("2", "DESPESAS", true, false),
            new PlanoConta("2.08", "CUSTOS OPERACIONAIS COM FROTA", true, false),
            new PlanoConta("2.08.001", "Combustiveis", false, false));

    @Test
    void shouldRateExpensesByTonnageProducingDifferentMargins() {
        DreClienteService service = service(
                List.of(
                        receita(10, "Cliente A", "1000.00"),
                        receita(20, "Cliente B", "1000.00"),
                        despesa("600.00", "2.08.001", "Combustiveis")),
                List.of(
                        new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10),
                        new DreClienteDriver(20, "Cliente B", new BigDecimal("300"), 30)));

        DreClienteSnapshot snapshot = service.dashboard(filtro(), null, null, "PESO", "CAIXA");

        assertEquals(new BigDecimal("2000.00"), snapshot.receitaTotal());
        assertEquals(new BigDecimal("600.00"), snapshot.despesaTotal());
        assertEquals(new BigDecimal("1400.00"), snapshot.resultadoTotal());

        DreClienteLinha a = linha(snapshot, 10);
        DreClienteLinha b = linha(snapshot, 20);
        assertEquals(new BigDecimal("150.00"), a.despesaApropriada());
        assertEquals(new BigDecimal("450.00"), b.despesaApropriada());
        assertEquals(new BigDecimal("850.00"), a.resultado());
        assertEquals(new BigDecimal("550.00"), b.resultado());
        assertEquals(new BigDecimal("85.00"), a.margemPct());
        assertEquals(new BigDecimal("55.00"), b.margemPct());
        assertNotEquals(0, a.margemPct().compareTo(b.margemPct()));

        // Reconciliacao: a soma dos rateios fecha com o bolo e a soma dos resultados com receita - despesa.
        BigDecimal somaDespesa = snapshot.linhas().stream().map(DreClienteLinha::despesaApropriada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaResultado = snapshot.linhas().stream().map(DreClienteLinha::resultado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(snapshot.despesaTotal(), somaDespesa);
        assertEquals(snapshot.resultadoTotal(), somaResultado);
    }

    @Test
    void shouldProduceUniformMarginWhenRatingByFreightValue() {
        DreClienteService service = service(
                List.of(
                        receita(10, "Cliente A", "1000.00"),
                        receita(20, "Cliente B", "1000.00"),
                        despesa("600.00", "2.08.001", "Combustiveis")),
                List.of(
                        new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10),
                        new DreClienteDriver(20, "Cliente B", new BigDecimal("300"), 30)));

        DreClienteSnapshot snapshot = service.dashboard(filtro(), null, null, "FRETE", "CAIXA");

        DreClienteLinha a = linha(snapshot, 10);
        DreClienteLinha b = linha(snapshot, 20);
        assertEquals(new BigDecimal("300.00"), a.despesaApropriada());
        assertEquals(new BigDecimal("300.00"), b.despesaApropriada());
        assertEquals(0, a.margemPct().compareTo(b.margemPct()));
        assertEquals(new BigDecimal("70.00"), a.margemPct());
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("margem % fica igual")));
    }

    @Test
    void shouldAlertClientsWithRevenueButNoTonnage() {
        DreClienteService service = service(
                List.of(
                        receita(10, "Cliente A", "1000.00"),
                        receita(20, "Cliente B", "1000.00"),
                        receita(30, "Cliente C", "500.00"),
                        despesa("600.00", "2.08.001", "Combustiveis")),
                List.of(
                        new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10),
                        new DreClienteDriver(20, "Cliente B", new BigDecimal("300"), 30)));

        DreClienteSnapshot snapshot = service.dashboard(filtro(), null, null, "PESO", "CAIXA");

        DreClienteLinha c = linha(snapshot, 30);
        assertEquals(new BigDecimal("0.00"), c.despesaApropriada());
        assertEquals(new BigDecimal("500.00"), c.resultado());
        assertEquals(new BigDecimal("100.00"), c.margemPct());
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("sem tonelada")));

        BigDecimal somaDespesa = snapshot.linhas().stream().map(DreClienteLinha::despesaApropriada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(snapshot.despesaTotal(), somaDespesa);
    }

    @Test
    void shouldBuildScaledSectionsForSingleClient() {
        DreClienteService service = service(
                List.of(
                        receita(10, "Cliente A", "1000.00"),
                        receita(20, "Cliente B", "1000.00"),
                        despesa("600.00", "2.08.001", "Combustiveis")),
                List.of(
                        new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10),
                        new DreClienteDriver(20, "Cliente B", new BigDecimal("300"), 30)));

        DreClienteDetalhe detalhe = service.dreDoCliente(10, filtro(), null, null, "PESO", "CAIXA");

        assertEquals(new BigDecimal("1000.00"), detalhe.receita());
        assertEquals(new BigDecimal("150.00"), detalhe.despesaApropriada());
        assertEquals(new BigDecimal("850.00"), detalhe.resultado());
        assertEquals(new BigDecimal("-150.00"),
                detalhe.secoes().stream().filter(s -> s.codigo().equals("CUSTOS_SERVICOS")).findFirst().orElseThrow().valor());
        assertEquals(new BigDecimal("850.00"),
                detalhe.linhas().stream().filter(l -> l.codigo().equals("RESULTADO_LIQUIDO")).findFirst().orElseThrow().valor());
    }

    @Test
    void shouldUseGerencialTonnageAndReportNaoAtribuido() {
        // driver 99 nao tem receita: sua tonelada entra no total (igual ao gerencial) mas vira "nao atribuido".
        DreClienteService service = service(
                List.of(
                        receita(10, "Cliente A", "1000.00"),
                        receita(20, "Cliente B", "1000.00"),
                        despesa("600.00", "2.08.001", "Combustiveis")),
                List.of(
                        new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10),
                        new DreClienteDriver(20, "Cliente B", new BigDecimal("300"), 30),
                        new DreClienteDriver(99, "Sem receita", new BigDecimal("100"), 5)));

        DreClienteSnapshot s = service.dashboard(filtro(), null, null, "PESO", "CAIXA");

        assertEquals(new BigDecimal("500"), s.toneladasTotal());
        assertEquals(new BigDecimal("100"), s.toneladasNaoAtribuidas());
        assertEquals(new BigDecimal("120.00"), s.despesaNaoAtribuida());
        assertEquals(new BigDecimal("1.20"), s.custoPorTonelada());
        assertEquals(new BigDecimal("120.00"), linha(s, 10).despesaApropriada());
        assertEquals(new BigDecimal("360.00"), linha(s, 20).despesaApropriada());
        // Headline reconcilia com o gerencial: receita - bolo cheio.
        assertEquals(new BigDecimal("1400.00"), s.resultadoTotal());
        BigDecimal somaApropriada = s.linhas().stream().map(DreClienteLinha::despesaApropriada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(s.despesaTotal().subtract(s.despesaNaoAtribuida()), somaApropriada);
        assertTrue(s.alertas().stream().anyMatch(a -> a.contains("Nao atribuido")));
    }

    @Test
    void shouldUseCompetenciaSourceAndFilterDespesaSemDmr() {
        FinanceiroFluxoCaixaRepository repo = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of(receita(20, "Caixa-only", "5000.00")); // nao deve ser usado em competencia
            }

            @Override
            public List<FinanceiroMovimento> listarMovimentosCompetencia(FinanceiroFiltro filtro) {
                return List.of(
                        receita(10, "Cliente A", "1000.00"),
                        despesaCompetencia("400.00", "2.08.001", "Combustiveis", "SIM"),
                        despesaCompetencia("200.00", "2.08.002", "Pneus", "NAO")); // dmr != SIM -> excluida
            }

            @Override
            public List<PlanoConta> listarPlanoContas() {
                return PLANO;
            }

            @Override
            public List<DreClienteDriver> listarDriversRateioPorCliente(FinanceiroFiltro filtro) {
                return List.of(new DreClienteDriver(10, "Cliente A", new BigDecimal("100"), 10));
            }
        };
        DreClienteService service = new DreClienteService(repo, CLOCK);

        DreClienteSnapshot s = service.dashboard(filtro(), null, null, "PESO", "COMPETENCIA");

        assertEquals("COMPETENCIA", s.regime());
        assertEquals(new BigDecimal("1000.00"), s.receitaTotal()); // veio da competencia, nao do caixa (5000)
        assertEquals(new BigDecimal("400.00"), s.despesaTotal());   // despesa sem dmr=SIM foi excluida
        assertEquals(new BigDecimal("400.00"), linha(s, 10).despesaApropriada());
        assertEquals(new BigDecimal("600.00"), linha(s, 10).resultado());
    }

    // ---- helpers ----

    private DreClienteService service(List<FinanceiroMovimento> movimentos, List<DreClienteDriver> drivers) {
        FinanceiroFluxoCaixaRepository repository = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return movimentos;
            }

            @Override
            public List<PlanoConta> listarPlanoContas() {
                return PLANO;
            }

            @Override
            public List<DreClienteDriver> listarDriversRateioPorCliente(FinanceiroFiltro filtro) {
                return drivers;
            }
        };
        return new DreClienteService(repository, CLOCK);
    }

    private FinanceiroFiltro filtro() {
        return new FinanceiroFiltro(INICIO, FIM, "", "REALIZADO", "TODAS");
    }

    private DreClienteLinha linha(DreClienteSnapshot snapshot, int idCliente) {
        return snapshot.linhas().stream().filter(l -> l.idCliente() == idCliente).findFirst().orElseThrow();
    }

    private FinanceiroMovimento receita(int idCliente, String nome, String valor) {
        return new FinanceiroMovimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO,
                FinanceiroOrigemTipo.FATURA_BAIXA, 1, INICIO, INICIO, INICIO, new BigDecimal(valor), 1, "Banco",
                idCliente, nome, 7, "Operacional", 1, "SPO", 18, "Receita de fretes", "1.01.001", "Receita", "FAT-1",
                "Baixa", false, false, LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento despesa(String valor, String classificacao, String plano) {
        return new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO,
                FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, 2, INICIO, INICIO, INICIO, new BigDecimal(valor), 2,
                "Banco", 702, "Fornecedor", 4, "Frota", 2, "SJP", 22, plano, classificacao, plano, "NC-1", "Duplicata",
                false, false, LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento despesaCompetencia(String valor, String classificacao, String plano, String dmr) {
        return new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO,
                FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, 2, INICIO, INICIO, null, new BigDecimal(valor), 2,
                "Banco", 702, "Fornecedor", 4, "Frota", 2, "SJP", 22, plano, classificacao, dmr, "NC-1", "Competencia",
                false, false, LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }
}
