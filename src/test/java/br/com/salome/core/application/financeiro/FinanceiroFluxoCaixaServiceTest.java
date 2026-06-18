package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void shouldRemoveReceitasDoTomadorExpressoSalomeAndBanco34() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "100.00", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "200.00", true, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, "300.00", false, true)
        ), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"));

        assertEquals(1, snapshot.movimentos().size());
        assertEquals(new BigDecimal("100.00"), snapshot.movimentos().getFirst().valor());
        assertTrue(snapshot.alertas().stream().anyMatch(alerta -> alerta.contains("Expresso Salome")));
    }

    @Test
    void shouldKeepDespesasDeExtratoAvulso() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, "80.00", false, false)
        ), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"));

        assertEquals(1, snapshot.movimentos().size());
        assertEquals(FinanceiroNatureza.DESPESA, snapshot.movimentos().getFirst().natureza());
        assertEquals(new BigDecimal("-80.00"), snapshot.kpis().getFirst().valor());
    }

    @Test
    void centroDeCustoCardShouldAggregateDespesasOnly() {
        FinanceiroFluxoCaixaService service = new FinanceiroFluxoCaixaService(filtro -> List.of(
                movimentoComCentro(FinanceiroNatureza.RECEITA, "500.00", "CentroReceita"),
                movimentoComCentro(FinanceiroNatureza.DESPESA, "120.00", "CentroDespesa")
        ), CLOCK);

        var snapshot = service.dashboard(new FinanceiroFiltro(INICIO, FIM, "", "TODOS", "TODAS"));

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
}
