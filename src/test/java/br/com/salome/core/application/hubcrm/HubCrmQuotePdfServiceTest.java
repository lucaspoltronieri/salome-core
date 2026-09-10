package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuotePrint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class HubCrmQuotePdfServiceTest {

    @Test
    void imprimeNoFormatoDoLegadoComDadosDaCotacaoEContatoComercial() throws Exception {
        HubCrmLegacyRepository legacy = mock(HubCrmLegacyRepository.class);
        when(legacy.findQuotePrint(15580L)).thenReturn(Optional.of(print("Fernanda")));

        String text = text(new HubCrmQuotePdfService(legacy).generate(15580L));

        assertThat(text).contains("COTAÇÃO DE FRETE: 15580", "Responsável:", "Fernanda",
                "(17) 2139-4866", "fernanda.silva@salome.com.br");
        assertThat(text).contains("Remetente:", "STERILEX CIENTIFICA LTDA", "R DO CORREGO 130",
                "SÃO PAULO-SP", "(11) 2606-5349", "Destinatário:", "Consignatário:");
        assertThat(text).contains("Tipo de Pagamento:", "Emitente (CIF)", "Natureza de Carga:", "DIVERSOS",
                "Total Frete:", "262,01", "Peso:", "12,300", "Informações:", "Coletar pela manhã");
    }

    @Test
    void queirozRecebeOEmailDaJaci() throws Exception {
        HubCrmLegacyRepository legacy = mock(HubCrmLegacyRepository.class);
        when(legacy.findQuotePrint(15580L)).thenReturn(Optional.of(print("QUEIROZ")));

        assertThat(text(new HubCrmQuotePdfService(legacy).generate(15580L)))
                .contains("jaci.queiroz@salome.com.br");
    }

    private String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
            return new PDFTextStripper().getText(document);
        }
    }

    private LegacyQuotePrint print(String responsible) {
        LegacyQuote quote = new LegacyQuote(
                15580L, LocalDate.of(2026, 8, 17), "11:05", responsible, "ABERTA",
                LocalDateTime.of(2026, 8, 17, 11, 5), "Emitente (CIF)",
                "03541994000141", "STERILEX CIENTIFICA LTDA", "SÃO PAULO",
                "08428051000120", "STERIMED CEDRAL SERVICOS DE ESTERILIZACAO LTDA", "CEDRAL",
                "03541994000141", "STERILEX CIENTIFICA LTDA", "1126065349", "fiscal@sterilex.com.br",
                "DIVERSOS", 1, new BigDecimal("12.30"), new BigDecimal("6924.50"), new BigDecimal("0.03"),
                new BigDecimal("62.52"), new BigDecimal("29.78"), new BigDecimal("8.28"),
                new BigDecimal("89.39"), BigDecimal.ZERO, new BigDecimal("18.44"), new BigDecimal("22.16"),
                BigDecimal.ZERO, new BigDecimal("31.44"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("262.01"), null, Map.of());
        return new LegacyQuotePrint(quote,
                new LegacyQuotePrint.Party("03541994000141", "STERILEX CIENTIFICA LTDA", "R DO CORREGO 130",
                        "", "PARQUE SAO PEDRO", "SÃO PAULO-SP", "08586250", "1126065349", "fiscal@sterilex.com.br"),
                new LegacyQuotePrint.Party("08428051000120", "STERIMED CEDRAL SERVICOS DE ESTERILIZACAO LTDA",
                        "AV BRASIL 10", "", "CENTRO", "CEDRAL-SP", "15895000", "", ""),
                new LegacyQuotePrint.Party("", "", "", "", "", "", "", "", ""),
                null, "Coletar pela manhã");
    }
}
