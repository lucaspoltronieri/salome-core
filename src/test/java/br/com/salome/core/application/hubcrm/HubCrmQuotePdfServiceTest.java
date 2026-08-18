package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class HubCrmQuotePdfServiceTest {

    @Test
    void incluiNumeroDaCotacaoLegadaNoCabecalho() throws Exception {
        HubCrmLegacyRepository legacy = mock(HubCrmLegacyRepository.class);
        LegacyQuote quote = quote(15580L);
        when(legacy.findQuotesByIds(List.of(15580L))).thenReturn(List.of(quote));

        byte[] pdf = new HubCrmQuotePdfService(legacy).generate(15580L);

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("COTAÇÃO DE FRETE", "Nº 15580", "TOTAL DO FRETE");
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }
    }

    private LegacyQuote quote(long id) {
        return new LegacyQuote(
                id, LocalDate.of(2026, 8, 17), "11:05", "Fernanda", "ABERTA",
                LocalDateTime.of(2026, 8, 17, 11, 5), "Emitente (CIF)",
                "03541994000141", "STERILEX CIENTIFICA LTDA", "SÃO PAULO-SP",
                "08428051000120", "STERIMED CEDRAL SERVICOS DE ESTERILIZACAO LTDA", "CEDRAL-SP",
                "03541994000141", "STERILEX CIENTIFICA LTDA", "1126065349", "fiscal@sterilex.com.br",
                "DIVERSOS", 1, new BigDecimal("12.30"), new BigDecimal("6924.50"), new BigDecimal("0.03"),
                new BigDecimal("62.52"), new BigDecimal("29.78"), new BigDecimal("8.28"),
                new BigDecimal("89.39"), BigDecimal.ZERO, new BigDecimal("18.44"), new BigDecimal("22.16"),
                BigDecimal.ZERO, new BigDecimal("31.44"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("262.01"), null, Map.of());
    }
}
