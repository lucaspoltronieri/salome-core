package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.FinanceiroContaNo;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroHorizonteCard;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroProjecaoPonto;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinanceiroFluxoCaixaServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);
    // 2026-06-08 e uma segunda-feira: amanha = 09 (terca), fim de semana = 14 (domingo), fim do mes = 30.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T12:00:00Z"), ZoneId.of("UTC"));
    private static final FinanceiroFiltro FILTRO = new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS");

    @Test
    void shouldRemoveReceitasDoTomadorExpressoSalomeAndBanco34() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "100.00", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "200.00", true, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "300.00", false, true)
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        assertEquals(1, snapshot.movimentos().size());
        assertEquals(new BigDecimal("100.00"), snapshot.movimentos().getFirst().valor());
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("Expresso Salome")));
    }

    @Test
    void shouldKeepDespesasDeExtratoAvulsoNoRetrospectivo() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, "80.00", false, false)
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        assertEquals(1, snapshot.movimentos().size());
        assertEquals(FinanceiroNatureza.DESPESA, snapshot.movimentos().getFirst().natureza());
        var extrato = snapshot.retrospectivo().stream()
                .filter(card -> card.titulo().equals("Pago via extrato bancario"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("80.00"), extrato.valor());
        assertEquals(1, extrato.quantidade());
    }

    @Test
    void shouldBucketContasAPagarPorHorizonte() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                previstoDespesa(LocalDate.of(2026, 6, 8), "40.00", "2.08.001"),
                previstoDespesa(LocalDate.of(2026, 6, 9), "100.00", "2.08.001"),
                previstoDespesa(LocalDate.of(2026, 6, 12), "200.00", "2.08.004"),
                previstoDespesa(LocalDate.of(2026, 6, 25), "300.00", "2.05.001"),
                previstoDespesa(LocalDate.of(2026, 6, 1), "50.00", "2.08.001")
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        // Hoje = 2026-06-08; cumulativo: hoje tambem entra em SEMANA e MES.
        assertEquals(new BigDecimal("40.00"), horizonte(snapshot.aPagar(), "HOJE").valor());
        assertEquals(new BigDecimal("100.00"), horizonte(snapshot.aPagar(), "AMANHA").valor());
        assertEquals(new BigDecimal("340.00"), horizonte(snapshot.aPagar(), "SEMANA").valor());
        assertEquals(new BigDecimal("640.00"), horizonte(snapshot.aPagar(), "MES").valor());
        assertEquals(new BigDecimal("50.00"), horizonte(snapshot.aPagar(), "ATRASO").valor());
    }

    @Test
    void shouldIgnorarPrevistosComValorZero() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                previstoReceita(FinanceiroOrigemTipo.FATURA_ABERTA, LocalDate.of(2026, 6, 25), "0.00", "1.01.001"),
                previstoReceita(FinanceiroOrigemTipo.FATURA_ABERTA, LocalDate.of(2026, 6, 25), "1000.00", "1.01.001")
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        FinanceiroHorizonteCard mes = horizonte(snapshot.aReceber(), "MES");
        assertEquals(new BigDecimal("1000.00"), mes.valor());
        assertEquals(1, mes.quantidade());
    }

    @Test
    void shouldSomarFaturaAbertaECteAbertoEmContasAReceberSemDuplicar() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                previstoReceita(FinanceiroOrigemTipo.FATURA_ABERTA, LocalDate.of(2026, 6, 10), "1000.00", "1.01.001"),
                previstoReceita(FinanceiroOrigemTipo.CTE_ABERTO, LocalDate.of(2026, 6, 9), "500.00", null)
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        assertEquals(new BigDecimal("500.00"), horizonte(snapshot.aReceber(), "AMANHA").valor());
        assertEquals(new BigDecimal("1500.00"), horizonte(snapshot.aReceber(), "SEMANA").valor());
        assertEquals(new BigDecimal("1500.00"), horizonte(snapshot.aReceber(), "MES").valor());
    }

    @Test
    void shouldProjetarSaldoConciliadoComSaldoBancario() {
        FinanceiroFluxoCaixaRepository repository = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of(
                        previstoDespesa(LocalDate.of(2026, 6, 1), "50.00", "2.08.001"),   // atrasado -> entra em hoje
                        previstoDespesa(LocalDate.of(2026, 6, 9), "100.00", "2.08.001"),
                        previstoDespesa(LocalDate.of(2026, 6, 12), "200.00", "2.08.004"),
                        previstoDespesa(LocalDate.of(2026, 6, 25), "300.00", "2.05.001"),
                        previstoReceita(FinanceiroOrigemTipo.CTE_ABERTO, LocalDate.of(2026, 6, 9), "500.00", "1.01.001"),
                        previstoReceita(FinanceiroOrigemTipo.FATURA_ABERTA, LocalDate.of(2026, 6, 10), "1000.00", "1.01.001"));
            }

            @Override
            public List<FinanceiroSaldoBanco> listarSaldosBancarios(FinanceiroFiltro filtro) {
                return List.of(new FinanceiroSaldoBanco(1, "Banco", new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"), BigDecimal.ZERO, false));
            }
        };
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(repository, CLOCK);

        var snapshot = service.dashboard(FILTRO);

        assertEquals(new BigDecimal("1000.00"), snapshot.saldoBancarioAtual());

        List<FinanceiroProjecaoPonto> projecao = snapshot.projecao();
        assertEquals(LocalDate.of(2026, 6, 8), projecao.getFirst().data());
        assertEquals(LocalDate.of(2026, 6, 30), projecao.getLast().data());
        // Hoje (08): so o atrasado de 50 -> 1000 - 50 = 950.
        assertEquals(new BigDecimal("950.00"), projecao.getFirst().saldoProjetado());
        // Fim do mes: 1000 + 1500 (receber) - 650 (pagar + atrasado) = 1850.
        assertEquals(new BigDecimal("1850.00"), projecao.getLast().saldoProjetado());

        assertEquals(new BigDecimal("1850.00"), kpi(snapshot, "Saldo projetado fim do mes"));
        assertEquals(new BigDecimal("600.00"), kpi(snapshot, "A pagar ate o fim do mes"));
        assertEquals(new BigDecimal("1500.00"), kpi(snapshot, "A receber ate o fim do mes"));
    }

    @Test
    void shouldMontarArvorePlanoContasComDocumentosNoDrill() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                previstoDespesa(LocalDate.of(2026, 6, 9), "100.00", "2.08.001"),
                previstoDespesa(LocalDate.of(2026, 6, 12), "200.00", "2.08.004"),
                previstoDespesa(LocalDate.of(2026, 6, 25), "300.00", "2.05.001")
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        FinanceiroHorizonteCard mes = horizonte(snapshot.aPagar(), "MES");
        assertEquals(1, mes.contas().size());
        FinanceiroContaNo raiz = mes.contas().getFirst();
        assertEquals("2", raiz.classificacao());
        assertEquals(new BigDecimal("600.00"), raiz.valor());
        assertTrue(raiz.sintetica());
        assertEquals(2, raiz.filhos().size());

        FinanceiroContaNo grupoFrota = filho(raiz, "2.08");
        FinanceiroContaNo combustivel = filho(grupoFrota, "2.08.001");
        assertFalse(combustivel.sintetica());
        assertEquals(1, combustivel.documentos().size());
        assertEquals(new BigDecimal("100.00"), combustivel.documentos().getFirst().valor());
    }

    @Test
    void shouldExcluirDespesasDuplicataAVista() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                duplicata(LocalDate.of(2026, 6, 9), "100.00", "1/1"),
                duplicata(LocalDate.of(2026, 6, 9), "999.00", "A VISTA"),
                duplicata(LocalDate.of(2026, 6, 12), "555.00", "À VISTA")
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        // Apenas a duplicata real (numero "1/1") entra; as duplicatas "A VISTA" sao caixa 2 e saem.
        FinanceiroHorizonteCard mes = horizonte(snapshot.aPagar(), "MES");
        assertEquals(new BigDecimal("100.00"), mes.valor());
        assertEquals(1, mes.quantidade());
        assertEquals(1, snapshot.movimentos().size());
    }

    @Test
    void centroDeCustoCardShouldAggregateDespesasOnly() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimentoComCentro(FinanceiroNatureza.RECEITA, "500.00", "CentroReceita"),
                movimentoComCentro(FinanceiroNatureza.DESPESA, "120.00", "CentroDespesa")
        ), CLOCK);

        var snapshot = service.dashboard(FILTRO);

        assertFalse(snapshot.porCentroCusto().stream().anyMatch(grupo -> grupo.chave().equals("CentroReceita")));
        assertTrue(snapshot.porCentroCusto().stream().anyMatch(grupo -> grupo.chave().equals("CentroDespesa")));
    }

    @Test
    void repositoryContractShouldExposeReadOnlyMethodsOnly() {
        for (Method method : FinanceiroFluxoCaixaRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("save"));
            assertFalse(name.startsWith("update"));
            assertFalse(name.startsWith("delete"));
            assertFalse(name.startsWith("create"));
            assertFalse(name.startsWith("insert"));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static FinanceiroHorizonteCard horizonte(List<FinanceiroHorizonteCard> cards, String codigo) {
        FinanceiroHorizonteCard card = cards.stream().filter(c -> c.codigo().equals(codigo)).findFirst().orElseThrow();
        assertNotNull(card);
        return card;
    }

    private static FinanceiroContaNo filho(FinanceiroContaNo pai, String classificacao) {
        return pai.filhos().stream().filter(no -> no.classificacao().equals(classificacao)).findFirst().orElseThrow();
    }

    private static BigDecimal kpi(br.com.salome.core.domain.financeiro.FinanceiroDashboardSnapshot snapshot, String titulo) {
        return snapshot.kpis().stream().filter(k -> k.titulo().equals(titulo)).findFirst().orElseThrow().valor();
    }

    private FinanceiroMovimento movimento(FinanceiroNatureza natureza, FinanceiroStatus status, String valor,
            boolean tomadorExpressoSalome, boolean bancoPerdasDanos) {
        return new FinanceiroMovimento(natureza, status, FinanceiroOrigemTipo.EXTRATO_AVULSO, 1, INICIO, INICIO, INICIO,
                new BigDecimal(valor), bancoPerdasDanos ? 34 : 1, bancoPerdasDanos ? "34 - Perdas e danos" : "Banco",
                10, tomadorExpressoSalome ? "Expresso Salome" : "Cliente", 1, "Centro", 1, "Filial", 1, "Plano", "1.01",
                "DMR", "DOC", "Historico", tomadorExpressoSalome, bancoPerdasDanos,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento movimentoComCentro(FinanceiroNatureza natureza, String valor, String centro) {
        return new FinanceiroMovimento(natureza, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.EXTRATO_AVULSO, 1,
                INICIO, INICIO, INICIO, new BigDecimal(valor), 1, "Banco", 10, "Cliente", 1, centro, 1, "Filial",
                1, "Plano", "1.01", "DMR", "DOC", "Historico", false, false,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento previstoDespesa(LocalDate vencimento, String valor, String classificacao) {
        return new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.PREVISTO,
                FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, 1, vencimento, vencimento, null, new BigDecimal(valor),
                2, "Bradesco", 700, "Fornecedor", 4, "Frota", 1, "SPO", 1, "Plano", classificacao, "DMR",
                "NC-" + valor, "Duplicata em aberto", false, false,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento duplicata(LocalDate vencimento, String valor, String numeroDuplicata) {
        return new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.PREVISTO,
                FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA, 1, vencimento, vencimento, null, new BigDecimal(valor),
                2, "Bradesco", 700, "Fornecedor", 4, "Frota", 1, "SPO", 1, "Plano", "2.08.001", "DMR",
                "NC-146823/" + numeroDuplicata, "Duplicata", false, false,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento previstoReceita(FinanceiroOrigemTipo tipo, LocalDate vencimento, String valor,
            String classificacao) {
        return new FinanceiroMovimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.PREVISTO, tipo, 1, vencimento,
                vencimento, null, new BigDecimal(valor), 1, "Itau", 1200, "Cliente", 7, "Operacional", 1, "SPO",
                1, "Plano", classificacao, "DMR", "FAT-" + valor, "A receber", false, false,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }
}
