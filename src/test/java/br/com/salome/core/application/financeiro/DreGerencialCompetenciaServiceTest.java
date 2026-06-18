package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.DreGerencialSnapshot;
import br.com.salome.core.domain.financeiro.DreSecao;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreGerencialCompetenciaServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void shouldBuildSectionsFromCompetenciaSourcesAndExcludeBanco34AndExpressoSalome() {
        DreGerencialCompetenciaService service = new DreGerencialCompetenciaService(repoCompetencia(List.of(
                cte("1000.00", INICIO, false, false),
                cte("250.00", INICIO, true, false),
                cte("300.00", INICIO, false, true),
                despesa(FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, "400.00", INICIO, "2.08.001", "Sim", "Custos operacionais frota"),
                despesa(FinanceiroOrigemTipo.EXTRATO_AVULSO, "50.00", INICIO, "2.05.001", "Sim", "Despesas financeiras")
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(2, snapshot.receitasExcluidasQuantidade());
        assertEquals(new BigDecimal("550.00"), snapshot.receitasExcluidasValor());
        assertEquals(new BigDecimal("1000.00"), secao(snapshot, "RECEITA").valor());
        assertEquals(new BigDecimal("-400.00"), secao(snapshot, "CUSTOS_SERVICOS").valor());
        assertEquals(new BigDecimal("-50.00"), secao(snapshot, "RESULTADO_FINANCEIRO").valor());
        assertLine(snapshot, "RECEITA_LIQUIDA", "1000.00");
        assertLine(snapshot, "RESULTADO_LIQUIDO", "550.00");
    }

    @Test
    void shouldFilterByDataCompetenciaNotByOtherDates() {
        // CT-e emitido fora do periodo (dataCompetencia em maio) nao deve entrar, mesmo que outras datas caiam no mes.
        DreGerencialCompetenciaService service = new DreGerencialCompetenciaService(repoCompetencia(List.of(
                cte("1000.00", INICIO, false, false),
                cte("777.00", LocalDate.of(2026, 5, 20), false, false)
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(new BigDecimal("1000.00"), secao(snapshot, "RECEITA").valor());
    }

    @Test
    void shouldUseCompetenciaSourceNotCashFlowSource() {
        // listarMovimentos (caixa) tem dados; listarMovimentosCompetencia esta vazio -> snapshot sem receita.
        FinanceiroFluxoCaixaRepository repository = new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of(cte("9999.00", INICIO, false, false));
            }
        };
        DreGerencialCompetenciaService service = new DreGerencialCompetenciaService(repository, CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertTrue(snapshot.secoes().stream().noneMatch(secao -> secao.codigo().equals("RECEITA")));
    }

    private FinanceiroFluxoCaixaRepository repoCompetencia(List<FinanceiroMovimento> movimentos) {
        return new FinanceiroFluxoCaixaRepository() {
            @Override
            public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
                return List.of();
            }

            @Override
            public List<FinanceiroMovimento> listarMovimentosCompetencia(FinanceiroFiltro filtro) {
                return movimentos;
            }
        };
    }

    private DreSecao secao(DreGerencialSnapshot snapshot, String codigo) {
        return snapshot.secoes().stream().filter(secao -> secao.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private void assertLine(DreGerencialSnapshot snapshot, String codigo, String valor) {
        var linha = snapshot.linhas().stream().filter(item -> item.codigo().equals(codigo)).findFirst().orElseThrow();
        assertEquals(new BigDecimal(valor), linha.valor());
    }

    private FinanceiroMovimento cte(String valor, LocalDate competencia, boolean tomadorExpresso, boolean bancoPerdasDanos) {
        return new FinanceiroMovimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO,
                FinanceiroOrigemTipo.CTE_EMITIDO, 1, competencia, competencia, null, new BigDecimal(valor), null, null,
                10, tomadorExpresso ? "Expresso Salome Ltda" : "Cliente", null, null, 1, "SPO", null, null, null, null,
                "CT-e 1", "CT-e emitido", tomadorExpresso, bancoPerdasDanos,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    private FinanceiroMovimento despesa(FinanceiroOrigemTipo origemTipo, String valor, LocalDate competencia,
            String classificacao, String dmr, String plano) {
        return new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, origemTipo, 1, competencia,
                competencia, null, new BigDecimal(valor), 1, "Banco", 10, "Fornecedor", 1, "Centro", 1, "SPO", 1, plano,
                classificacao, dmr, "DOC", plano, false, false,
                LegacyOrigin.of("classe", "metodo", "query", "tabela"));
    }

    @Test
    void shouldKeepOnlyDespesasWithDmrSimAndNotStartingWith1KeepingSemPlano() {
        DreGerencialCompetenciaService service = new DreGerencialCompetenciaService(repoCompetencia(List.of(
                cte("1000.00", INICIO, false, false),
                // entra: 2.x com dmr=Sim
                despesa(FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, "400.00", INICIO, "2.08.001", "Sim", "Custos frota"),
                // sai: dmr != Sim
                despesa(FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, "999.00", INICIO, "2.08.002", "Nao", "Custos frota"),
                // sai: classificacao inicia com 1 (conta de receita lancada como despesa)
                despesa(FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, "777.00", INICIO, "1.01.001", "Sim", "Receita"),
                // mantem como hoje: despesa sem plano de contas -> Administrativo
                despesa(FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, "120.00", INICIO, null, null, null)
        )), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"), null);

        assertEquals(new BigDecimal("-400.00"), secao(snapshot, "CUSTOS_SERVICOS").valor());
        assertEquals(new BigDecimal("-120.00"), secao(snapshot, "DESPESAS_ADMINISTRATIVAS").valor());
        assertTrue(snapshot.secoes().stream().flatMap(s -> s.contas().stream())
                .map(c -> c.classificacao() == null ? "" : c.classificacao())
                .noneMatch(cod -> cod.startsWith("1")));
    }
}
