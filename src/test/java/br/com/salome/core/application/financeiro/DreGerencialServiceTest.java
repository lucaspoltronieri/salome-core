package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.DreContaNo;
import br.com.salome.core.domain.financeiro.DreGerencialSnapshot;
import br.com.salome.core.domain.financeiro.DreSecao;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class DreGerencialServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void shouldBuildDreSectionsAndExcludeBanco34AndExpressoSalome() {
        DreGerencialService service = new DreGerencialService(repo(List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "1000.00", 1, "SPO", false, false, "3.01.01", "Receita frete"),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "250.00", 1, "SPO", true, false, "3.01.01", "Receita frete"),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "300.00", 2, "SJP", false, true, "3.01.01", "Receita frete"),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "400.00", 2, "SJP", false, false, "2.08.001", "Custos operacionais frota"),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.EXTRATO_AVULSO, "50.00", 1, "SPO", false, false, "2.05.001", "Despesas financeiras")
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(2, snapshot.receitasExcluidasQuantidade());
        assertEquals(new BigDecimal("550.00"), snapshot.receitasExcluidasValor());
        assertEquals(new BigDecimal("1000.00"), secao(snapshot, "RECEITA").valor());
        assertEquals(new BigDecimal("-400.00"), secao(snapshot, "CUSTOS_SERVICOS").valor());
        assertEquals(new BigDecimal("-50.00"), secao(snapshot, "RESULTADO_FINANCEIRO").valor());
        assertLine(snapshot, "RECEITA_LIQUIDA", "1000.00");
        assertLine(snapshot, "RESULTADO_LIQUIDO", "550.00");
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("receita(s) removida(s)")));
    }

    @Test
    void shouldClassifyNotaSemRateioAsAdministrativoSemCentroCusto() {
        DreGerencialService service = new DreGerencialService(repo(List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "1000.00", 1, "SPO", false, false, "3.01.01", "Receita frete"),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "120.00", 1, "SPO", false, false, null, null)
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        DreSecao administrativo = secao(snapshot, "DESPESAS_ADMINISTRATIVAS");
        assertEquals(new BigDecimal("-120.00"), administrativo.valor());
        DreContaNo semCentro = buscarConta(administrativo.contas(), c -> c.descricao().equals("Sem centro de custo"));
        assertNotNull(semCentro);
        assertEquals(new BigDecimal("-120.00"), semCentro.valor());
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("sem centro de custo")));
    }

    @Test
    void shouldClassifyExtratoSemDuplicataAsDespesasBancariasExtrato() {
        DreGerencialService service = new DreGerencialService(repo(List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "1000.00", 1, "SPO", false, false, "3.01.01", "Receita frete"),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.EXTRATO_AVULSO, "33.90", 1, "SPO", false, false, null, null)
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        DreSecao financeiro = secao(snapshot, "RESULTADO_FINANCEIRO");
        DreContaNo bancarias = buscarConta(financeiro.contas(), c -> c.descricao().equals("Despesas Bancarias Extrato"));
        assertNotNull(bancarias);
        assertEquals(new BigDecimal("-33.90"), bancarias.valor());
        assertEquals("EXTRATO_AVULSO", bancarias.origens().get(0).origemTipo());
    }

    @Test
    void shouldRollupSyntheticFromAnalyticClassifications() {
        FinanceiroFluxoCaixaRepository repository = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of(
                        movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "1000.00", 1, "SPO", false, false, "3.01.01", "Receita frete"),
                        movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "100.00", 1, "SPO", false, false, "2.08.001", "Combustivel"),
                        movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "200.00", 1, "SPO", false, false, "2.08.009", "Pneus"));
            }

            @Override
            public List<PlanoConta> listarPlanoContas() {
                return List.of(
                        new PlanoConta("2", "DESPESAS", true, false),
                        new PlanoConta("2.08", "CUSTOS OPERACIONAIS COM FROTA", true, false));
            }
        };
        DreGerencialService service = new DreGerencialService(repository, CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        DreSecao custos = secao(snapshot, "CUSTOS_SERVICOS");
        assertEquals(new BigDecimal("-300.00"), custos.valor());
        DreContaNo grupo = buscarConta(custos.contas(), c -> c.classificacao().equals("2.08"));
        assertNotNull(grupo);
        assertTrue(grupo.sintetica());
        assertEquals(new BigDecimal("-300.00"), grupo.valor());
        assertEquals(2, grupo.filhos().size());
        assertEquals("CUSTOS OPERACIONAIS COM FROTA", grupo.descricao());
    }

    @Test
    void shouldFilterByFilialWithoutBreakingConsolidadoDefault() {
        DreGerencialService service = new DreGerencialService(repo(List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "100.00", 1, "SPO", false, false, "3.01.01", "Receita"),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "200.00", 2, "SJP", false, false, "3.01.01", "Receita")
        )), CLOCK);

        var consolidado = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);
        var filialSjp = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), 2);
        var filialPorSigla = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null, "SJP");

        assertEquals(new BigDecimal("300.00"), secao(consolidado, "RECEITA").valor());
        assertEquals(new BigDecimal("200.00"), secao(filialSjp, "RECEITA").valor());
        assertEquals(new BigDecimal("200.00"), secao(filialPorSigla, "RECEITA").valor());
    }

    @Test
    void shouldComputeCustoPorToneladaFromDespesasAndTonnage() {
        FinanceiroFluxoCaixaRepository repository = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of(
                        movimento(FinanceiroNatureza.RECEITA, FinanceiroOrigemTipo.FATURA_BAIXA, "1000.00", 1, "SPO", false, false, "3.01.01", "Receita frete"),
                        movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "400.00", 1, "SPO", false, false, "2.08.001", "Custos operacionais frota"),
                        movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.EXTRATO_AVULSO, "50.00", 1, "SPO", false, false, "2.05.001", "Despesas financeiras"));
            }

            @Override
            public BigDecimal somarToneladasTransportadas(FinanceiroFiltro filtro) {
                return new BigDecimal("90.00");
            }
        };
        DreGerencialService service = new DreGerencialService(repository, CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(new BigDecimal("450.00"), snapshot.custoTotalPeriodo());
        assertEquals(new BigDecimal("90.00"), snapshot.toneladasTransportadas());
        assertEquals(new BigDecimal("5.00"), snapshot.custoPorTonelada());
    }

    @Test
    void shouldReturnZeroCustoPorToneladaWhenNoTonnage() {
        DreGerencialService service = new DreGerencialService(repo(List.of(
                movimento(FinanceiroNatureza.DESPESA, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, "400.00", 1, "SPO", false, false, "2.08.001", "Custos operacionais frota")
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(new BigDecimal("400.00"), snapshot.custoTotalPeriodo());
        assertEquals(BigDecimal.ZERO, snapshot.toneladasTransportadas());
        assertEquals(BigDecimal.ZERO, snapshot.custoPorTonelada());
    }

    @Test
    void repositoryContractShouldRemainReadOnly() {
        for (Method method : FinanceiroFluxoCaixaRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("save"));
            assertFalse(name.startsWith("update"));
            assertFalse(name.startsWith("delete"));
            assertFalse(name.startsWith("create"));
            assertFalse(name.startsWith("insert"));
        }
    }

    private FinanceiroFluxoCaixaRepository repo(List<FinanceiroMovimento> movimentos) {
        return filtro -> movimentos;
    }

    private DreSecao secao(DreGerencialSnapshot snapshot, String codigo) {
        return snapshot.secoes().stream().filter(secao -> secao.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private DreContaNo buscarConta(List<DreContaNo> contas, Predicate<DreContaNo> condicao) {
        for (DreContaNo conta : contas) {
            if (condicao.test(conta)) {
                return conta;
            }
            DreContaNo encontrada = buscarConta(conta.filhos(), condicao);
            if (encontrada != null) {
                return encontrada;
            }
        }
        return null;
    }

    private void assertLine(DreGerencialSnapshot snapshot, String codigo, String valor) {
        var linha = snapshot.linhas().stream().filter(item -> item.codigo().equals(codigo)).findFirst().orElseThrow();
        assertEquals(new BigDecimal(valor), linha.valor());
    }

    private FinanceiroMovimento movimento(FinanceiroNatureza natureza, FinanceiroOrigemTipo origemTipo, String valor,
            Integer filialId, String filial, boolean tomadorExpresso, boolean bancoPerdasDanos, String classificacao,
            String plano) {
        return new FinanceiroMovimento(natureza, FinanceiroStatus.REALIZADO, origemTipo, 1, INICIO, INICIO, INICIO,
                new BigDecimal(valor), bancoPerdasDanos ? 34 : 1,
                bancoPerdasDanos ? "34 - Perdas e Danos" : "Banco", 10,
                tomadorExpresso ? "Expresso Salome Ltda" : "Pessoa", 1, "Centro", filialId, filial, 1, plano,
                classificacao, plano, "DOC", plano, tomadorExpresso, bancoPerdasDanos,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }
}
