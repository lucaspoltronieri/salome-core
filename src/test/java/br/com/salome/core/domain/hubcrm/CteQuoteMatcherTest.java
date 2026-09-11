package br.com.salome.core.domain.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CteQuoteMatcherTest {
    private static final String PAGADOR = "12345678000190";

    @Test
    void aprovaComPagadorPesoNfEFreteIguaisMesmoComRemetenteEDestinatarioDiferentes() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyCte cte = cte(LocalDate.of(2026, 9, 3), "150.00", "11111111000111", "22222222000122");

        var match = CteQuoteMatcher.evaluate(cte, List.of(quote), 30);

        assertThat(match.outcome()).isEqualTo(CteQuoteMatcher.Outcome.APROVAR);
        assertThat(match.quote().id()).isEqualTo(1);
        assertThat(match.divergences()).contains("remetente").contains("destinatário").contains("volumes");
    }

    @Test
    void pagadorDiferenteNaoAprova() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyCte cte = new LegacyCte(10, "5000", "1", "chave", LocalDate.of(2026, 9, 2), "10:00", "CIF",
                PAGADOR, "98765432000110", "99999999000199", new BigDecimal("50"), new BigDecimal("1000"), 2,
                new BigDecimal("150.00"));

        assertThat(CteQuoteMatcher.evaluate(cte, List.of(quote), 30).outcome())
                .isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
    }

    @Test
    void freteDiferenteNaoAprovaSemCubagem() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyCte cte = cte(LocalDate.of(2026, 9, 2), "120.00", PAGADOR, "98765432000110");

        assertThat(CteQuoteMatcher.evaluate(cte, List.of(quote), 30).outcome())
                .isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
    }

    @Test
    void cubagemEsquecidaNaEmissaoAprovaComFreteMenor() {
        LegacyQuote quote = quote(1, "ABERTA", "JACI", LocalDate.of(2026, 9, 1), "150.00", "0.8");
        LegacyCte cte = cte(LocalDate.of(2026, 9, 2), "120.00", PAGADOR, "98765432000110");

        var match = CteQuoteMatcher.evaluate(cte, List.of(quote), 30);

        assertThat(match.outcome()).isEqualTo(CteQuoteMatcher.Outcome.APROVAR);
        assertThat(match.criteria()).contains("cubagem");
    }

    @Test
    void freteMaiorQueCotacaoNaoAprovaMesmoComCubagem() {
        LegacyQuote quote = quote(1, "ABERTA", "JACI", LocalDate.of(2026, 9, 1), "150.00", "0.8");
        LegacyCte cte = cte(LocalDate.of(2026, 9, 2), "180.00", PAGADOR, "98765432000110");

        assertThat(CteQuoteMatcher.evaluate(cte, List.of(quote), 30).outcome())
                .isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
    }

    @Test
    void pesoOuNotaDiferentesNaoAprovam() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyCte pesoDiferente = new LegacyCte(10, "5000", "1", "chave", LocalDate.of(2026, 9, 2), "10:00",
                "CIF", PAGADOR, "98765432000110", PAGADOR, new BigDecimal("80"), new BigDecimal("1000"), 2,
                new BigDecimal("150.00"));
        LegacyCte notaDiferente = new LegacyCte(11, "5001", "1", "chave", LocalDate.of(2026, 9, 2), "10:00",
                "CIF", PAGADOR, "98765432000110", PAGADOR, new BigDecimal("50"), new BigDecimal("1500"), 2,
                new BigDecimal("150.00"));

        assertThat(CteQuoteMatcher.evaluate(pesoDiferente, List.of(quote), 30).outcome())
                .isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
        assertThat(CteQuoteMatcher.evaluate(notaDiferente, List.of(quote), 30).outcome())
                .isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
    }

    @Test
    void arredondamentoDentroDaToleranciaAprova() {
        assertThat(CteQuoteMatcher.same(new BigDecimal("1000.00"), new BigDecimal("1009.00"))).isTrue();
        assertThat(CteQuoteMatcher.same(new BigDecimal("50.0"), new BigDecimal("50.9"))).isTrue();
        assertThat(CteQuoteMatcher.same(new BigDecimal("1000.00"), new BigDecimal("1020.00"))).isFalse();
    }

    @Test
    void cteAnteriorOuForaDaJanelaNaoAprova() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 10), "150.00", "0");

        assertThat(CteQuoteMatcher.evaluate(cte(LocalDate.of(2026, 9, 9), "150.00", PAGADOR, "x"),
                List.of(quote), 30).outcome()).isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
        assertThat(CteQuoteMatcher.evaluate(cte(LocalDate.of(2026, 10, 11), "150.00", PAGADOR, "x"),
                List.of(quote), 30).outcome()).isEqualTo(CteQuoteMatcher.Outcome.SEM_COTACAO);
        assertThat(CteQuoteMatcher.evaluate(cte(LocalDate.of(2026, 10, 10), "150.00", PAGADOR, "x"),
                List.of(quote), 30).outcome()).isEqualTo(CteQuoteMatcher.Outcome.APROVAR);
    }

    @Test
    void naoAprovadaSoRevertePararesponsaveisDoArpa() {
        LegacyQuote fernanda = quote(1, "NÃO APROVADA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyQuote carlos = quote(2, "NÃO APROVADA", "CARLOS", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyQuote carlosAberta = quote(3, "ABERTA", "CARLOS", LocalDate.of(2026, 9, 1), "150.00", "0");

        assertThat(CteQuoteMatcher.eligibleStatus(fernanda)).isTrue();
        assertThat(CteQuoteMatcher.eligibleStatus(carlos)).isFalse();
        assertThat(CteQuoteMatcher.eligibleStatus(carlosAberta)).isTrue();
        assertThat(CteQuoteMatcher.eligibleStatus(quote(4, "APROVADA", "FERNANDA",
                LocalDate.of(2026, 9, 1), "150.00", "0"))).isFalse();
    }

    @Test
    void escolheCotacaoMaisProximaDaEmissao() {
        LegacyQuote antiga = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 1), "150.00", "0");
        LegacyQuote recente = quote(2, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 5), "150.00", "0");

        var match = CteQuoteMatcher.evaluate(cte(LocalDate.of(2026, 9, 6), "150.00", PAGADOR, "x"),
                List.of(antiga, recente), 30);

        assertThat(match.outcome()).isEqualTo(CteQuoteMatcher.Outcome.APROVAR);
        assertThat(match.quote().id()).isEqualTo(2);
    }

    @Test
    void empateNoMesmoDiaEFreteFicaAmbiguo() {
        LegacyQuote a = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 9, 5), "150.00", "0");
        LegacyQuote b = quote(2, "ABERTA", "JACI", LocalDate.of(2026, 9, 5), "150.00", "0");

        var match = CteQuoteMatcher.evaluate(cte(LocalDate.of(2026, 9, 6), "150.00", PAGADOR, "x"),
                List.of(a, b), 30);

        assertThat(match.outcome()).isEqualTo(CteQuoteMatcher.Outcome.AMBIGUO);
    }

    private static LegacyCte cte(LocalDate issue, String freight, String sender, String recipient) {
        return new LegacyCte(10, "5000", "1", "chave", issue, "10:00", "CIF", sender, recipient, PAGADOR,
                new BigDecimal("50"), new BigDecimal("1000"), 3, new BigDecimal(freight));
    }

    static LegacyQuote quote(long id, String status, String responsible, LocalDate created, String freight,
            String cubage) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:30", responsible, status,
                LocalDateTime.of(created, java.time.LocalTime.of(11, 0)), "Emitente (CIF)",
                PAGADOR, "REMETENTE LTDA", "SÃO PAULO-SP",
                "98765432000110", "DESTINATÁRIO LTDA", "CAMPINAS-SP",
                PAGADOR, "REMETENTE LTDA", "11999999999", "vendas@remetente.com",
                "DIVERSOS", 2, new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal(cubage),
                zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, new BigDecimal(freight),
                "Maria", Map.of());
    }
}
