package br.com.salome.core.infrastructure.legacy.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.salome.core.infrastructure.legacy.financeiro.CteVencimentoPrevisao.Parcela;
import br.com.salome.core.infrastructure.legacy.financeiro.CteVencimentoPrevisao.Previsao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CteVencimentoPrevisaoTest {

    private final CteVencimentoPrevisao previsao = new CteVencimentoPrevisao();

    // Emissao de referencia: 2025-01-06 e uma segunda-feira.
    private static final LocalDate SEGUNDA = LocalDate.of(2025, 1, 6);
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @Test
    void padraoEmissaoAteDia15VenceDia30() {
        List<Parcela> parcelas = previsao.resolver("Cliente Qualquer", null, SEGUNDA, CEM);
        assertEquals(1, parcelas.size());
        assertEquals(LocalDate.of(2025, 1, 30), parcelas.get(0).vencimento());
        assertEquals(CEM, parcelas.get(0).valor());
    }

    @Test
    void padraoEmissaoApos15VenceDia15DoMesSeguinte() {
        List<Parcela> parcelas = previsao.resolver("Cliente Qualquer", null, LocalDate.of(2025, 1, 20), CEM);
        assertEquals(LocalDate.of(2025, 2, 15), parcelas.get(0).vencimento());
    }

    @Test
    void ajoferFechaSegundaMais21CaindoNaSextaMaisProxima() {
        // Fechamento 06/01 (seg) + 21 = 27/01 (seg); sexta mais proxima = 24/01 (3 dias antes).
        List<Parcela> parcelas = previsao.resolver("TRANSPORTADORA AJOFER LTDA", null, SEGUNDA, CEM);
        assertEquals(1, parcelas.size());
        assertEquals(LocalDate.of(2025, 1, 24), parcelas.get(0).vencimento());
    }

    @Test
    void akzoFechaTercaMais60NoDia04MaisProximo() {
        // Fechamento 07/01 (ter) + 60 = 08/03; dia 04 mais proximo = 04/03.
        List<Parcela> parcelas = previsao.resolver("AKZO NOBEL LTDA", null, SEGUNDA, CEM);
        assertEquals(LocalDate.of(2025, 3, 4), parcelas.get(0).vencimento());
    }

    @Test
    void iquineFechaQuartaMais21() {
        List<Parcela> parcelas = previsao.resolver("TINTAS IQUINE", null, SEGUNDA, CEM);
        assertEquals(LocalDate.of(2025, 1, 29), parcelas.get(0).vencimento());
    }

    @Test
    void dovacGeraTresParcelas45_60_75ComSomaIgualAoTotal() {
        List<Parcela> parcelas = previsao.resolver("DOVAC DISTRIBUIDORA", null, SEGUNDA, CEM);
        assertEquals(3, parcelas.size());
        assertEquals(LocalDate.of(2025, 3, 1), parcelas.get(0).vencimento());
        assertEquals(LocalDate.of(2025, 3, 16), parcelas.get(1).vencimento());
        assertEquals(LocalDate.of(2025, 3, 31), parcelas.get(2).vencimento());
        BigDecimal soma = parcelas.stream().map(Parcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(CEM, soma);
    }

    @Test
    void anjoPrimeiraSemanaVenceDia15DoMesCorrente() {
        List<Parcela> parcelas = previsao.resolver("ANJO TINTAS", null, SEGUNDA, CEM);
        assertEquals(LocalDate.of(2025, 1, 15), parcelas.get(0).vencimento());
    }

    @Test
    void braileEmissaoAteDia15VenceDia15DoMesSeguinte() {
        List<Parcela> parcelas = previsao.resolver("BRAILE INDUSTRIA", null, SEGUNDA, CEM);
        assertEquals(1, parcelas.size());
        assertEquals(LocalDate.of(2025, 2, 15), parcelas.get(0).vencimento());
        assertEquals(CEM, parcelas.get(0).valor());
    }

    @Test
    void braileEmissaoApos15VenceUltimoDiaDoMesSeguinte() {
        // Emissao 20/01 -> dia 30 do mes seguinte (fev/2025 tem 28 dias).
        List<Parcela> parcelas = previsao.resolver("BRAILE INDUSTRIA", null, LocalDate.of(2025, 1, 20), CEM);
        assertEquals(1, parcelas.size());
        assertEquals(LocalDate.of(2025, 2, 28), parcelas.get(0).vencimento());
    }

    @Test
    void identificacaoIgnoraAcentoECaixa() {
        List<Parcela> comAcento = previsao.resolver("Eucatex Indústria", null, SEGUNDA, CEM);
        assertEquals(LocalDate.of(2025, 1, 27), comAcento.get(0).vencimento());
    }

    @Test
    void parcelaExpoeOFechamento() {
        // Sherwin fecha na segunda da emissao (06/01); Dovac fecha quinzenal (dia 15).
        assertEquals(LocalDate.of(2025, 1, 6),
                previsao.resolver("SHERWIN WILLIAMS", null, SEGUNDA, CEM).get(0).fechamento());
        assertEquals(LocalDate.of(2025, 1, 15),
                previsao.resolver("DOVAC DISTRIBUIDORA", null, SEGUNDA, CEM).get(0).fechamento());
    }

    @Test
    void preverNoDiaDoFechamentoAindaMostraPrevisaoNormal() {
        // Sherwin: fechamento 06/01. Em 06/01 (== fechamento) ainda mostra a previsao normal.
        List<Previsao> previsoes = previsao.prever("SHERWIN WILLIAMS", null, SEGUNDA, CEM, LocalDate.of(2025, 1, 6));
        assertEquals(1, previsoes.size());
        assertEquals(LocalDate.of(2025, 1, 31), previsoes.get(0).vencimento());
        assertEquals(false, previsoes.get(0).aFaturarAtrasado());
    }

    @Test
    void preverComFechamentoVencidoViraUmaEntradaAFaturar() {
        // Sherwin: fechamento 06/01. Em 07/01 (dia seguinte) vira "a faturar" com venc = fechamento.
        List<Previsao> previsoes = previsao.prever("SHERWIN WILLIAMS", null, SEGUNDA, CEM, LocalDate.of(2025, 1, 7));
        assertEquals(1, previsoes.size());
        assertEquals(LocalDate.of(2025, 1, 6), previsoes.get(0).vencimento());
        assertEquals(CEM, previsoes.get(0).valor());
        assertEquals(true, previsoes.get(0).aFaturarAtrasado());
    }

    @Test
    void preverDovacVencidoColapsaAsParcelasNumaSoAFaturar() {
        // Dovac: fechamento 15/01. Em 16/01 as 3 parcelas viram UMA entrada "a faturar" com valor cheio.
        List<Previsao> previsoes = previsao.prever("DOVAC DISTRIBUIDORA", null, SEGUNDA, CEM, LocalDate.of(2025, 1, 16));
        assertEquals(1, previsoes.size());
        assertEquals(LocalDate.of(2025, 1, 15), previsoes.get(0).vencimento());
        assertEquals(CEM, previsoes.get(0).valor());
        assertEquals(true, previsoes.get(0).aFaturarAtrasado());
    }

    @Test
    void preverDovacNoDiaDoFechamentoMantemAsTresParcelas() {
        List<Previsao> previsoes = previsao.prever("DOVAC DISTRIBUIDORA", null, SEGUNDA, CEM, LocalDate.of(2025, 1, 15));
        assertEquals(3, previsoes.size());
        assertEquals(false, previsoes.get(0).aFaturarAtrasado());
        BigDecimal soma = previsoes.stream().map(Previsao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(CEM, soma);
    }
}
