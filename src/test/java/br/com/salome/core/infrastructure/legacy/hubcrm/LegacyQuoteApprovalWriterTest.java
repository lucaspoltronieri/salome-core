package br.com.salome.core.infrastructure.legacy.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyQuoteApprovalWriterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 14, 0);
    private JdbcTemplate jdbc;
    private LegacyQuoteApprovalWriter writer;

    /** CT-e 320274 da cotação 15813: mesmo peso, NF final maior e frete R$ 1,02 acima. */
    private final LegacyCte cte = new LegacyCte(710005, "320274", "1", "k", LocalDate.of(2026, 9, 15), "19:10",
            "Emitente (CIF)", "09048147000126", "27775101000190", "09048147000126", new BigDecimal("104.800"),
            new BigDecimal("2693.92"), 4, new BigDecimal("187.94000"),
            new LegacyCte.Charges(new BigDecimal("63.06"), new BigDecimal("8.68"), new BigDecimal("12.97"),
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("76.34"), new BigDecimal("4.34"),
                    BigDecimal.ZERO, new BigDecimal("22.55"), BigDecimal.ZERO, BigDecimal.ZERO));

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        writer = new LegacyQuoteApprovalWriter(jdbc,
                new HubCrmAutoApprovalProperties(true, 30, 3, "", "crm_api", "", "Outro"));
    }

    @Test
    void ajustaSoOsValoresQueMudamParaOsDoCte() {
        var changes = LegacyQuoteApprovalWriter.valueChanges(quote("ABERTA"), cte);

        assertThat(changes).extracting(LegacyQuoteApprovalWriter.ValueChange::column)
                .containsExactly("valorNf", "freteValor", "grisValor", "icmsValor", "totalFrete");
    }

    @Test
    void cotacaoJaAprovadaAMaoEReaprovadaSemMudarADataDaAprovacao() {
        assertThat(writer.approve(quote("APROVADA"), cte, NOW, true)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue())
                .doesNotContain("status='APROVADA'").doesNotContain("statusData")
                .contains("contatoAprovacao=?", "valorNf=?", "totalFrete=?")
                .endsWith("WHERE idCotacao=? AND status=?");
        List<Object> values = Arrays.asList(args.getValue());
        assertThat(values).contains("HUB CRM - CT-e 320274/1", new BigDecimal("2693.92"), 15813L, "APROVADA");
        assertThat(values).anySatisfy(value -> assertThat(String.valueOf(value))
                .contains("[crm_api] [VALORNF] [2507.0] [2693.92]")
                .contains("[TOTALFRETE] [186.92] [187.94]")
                .doesNotContain("[status]"));
    }

    @Test
    void cotacaoAbertaSemAjusteSoAprova() {
        assertThat(writer.approve(quote("ABERTA"), cte, NOW, false)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("status='APROVADA'", "statusData=?").doesNotContain("valorNf");
    }

    private static LegacyQuote quote(String status) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(15813, LocalDate.of(2026, 9, 14), "10:06", "FERNANDA", status,
                LocalDateTime.of(2026, 9, 18, 9, 11), "Emitente (CIF)", "09048147000126", "IKTEC", "CAMPINAS",
                "27775101000190", "M GONCALVES", "S J RIO PRETO", "09048147000126", "IKTEC", "", "", "DIVERSOS", 4,
                new BigDecimal("104.800"), new BigDecimal("2507.00000"), new BigDecimal("0.130"),
                new BigDecimal("63.06"), new BigDecimal("8.08"), new BigDecimal("12.97"), zero, zero,
                new BigDecimal("76.34"), new BigDecimal("4.04"), zero, new BigDecimal("22.43"), zero, zero,
                new BigDecimal("186.92"), "IKTEC", Map.of());
    }
}
