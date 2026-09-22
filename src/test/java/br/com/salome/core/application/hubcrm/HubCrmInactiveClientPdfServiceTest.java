package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class HubCrmInactiveClientPdfServiceTest {

    @Test
    void listaOsCtesDoAnoComRemetenteDestinatarioETotais() throws Exception {
        InactiveClientRepository repository = mock(InactiveClientRepository.class);
        when(repository.findReport(26055L, 2025)).thenReturn(Optional.of(report(List.of(
                cte(317790, "Emitente (CIF)", "MANDARIM TINTAS", "DESTINO LTDA", "1001", "12.5", "1000", "110.45"),
                cte(317791, "Destinatário (FOB)", "FORNECEDOR SA", "MANDARIM TINTAS", "2002, 2003", "30", "2500",
                        "676.84")))));

        String text = text(new HubCrmInactiveClientPdfService(repository).generate(26055L, 2025));

        assertThat(text).contains("TRANSPORTES DO CLIENTE — 2025", "MANDARIM TINTAS RIO PRETO LOJA 11 LTDA",
                "(BARATO TINTAS)", "48.108.565/0001-13", "SÃO JOSÉ DO RIO PRETO-SP", "(17) 2139-4866");
        assertThat(text).contains("Nº CT-e", "Cidade remetente", "Cidade destinatário", "Volumes");
        assertThat(text).contains("317790", "CIF", "FOB", "FORNECEDOR SA", "SÃO PAULO", "CEDRAL", "2002, 2003",
                "TOTAL (2 CT-es)", "42,500", "3.500,00", "787,29", "7 volumes");
        // CNPJ só da outra parte: destinatário no CT-e CIF, remetente no CT-e FOB.
        assertThat(text).contains("CNPJ 44.555.666/0001-99", "CNPJ 11.222.333/0001-81")
                .doesNotContain("CNPJ 48.108.565/0001-13");
    }

    @Test
    void quebraEmVariasPaginasRepetindoOCabecalhoDaTabela() throws Exception {
        List<InactiveClientReport.Cte> ctes = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            ctes.add(cte(300000 + i, "Emitente (CIF)", "MANDARIM TINTAS", "CLIENTE " + i, "", "1", "10", "1"));
        }
        byte[] pdf = new HubCrmInactiveClientPdfService(mock(InactiveClientRepository.class)).generate(report(ctes));

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("(continuação)", "Remetente", "Destinatário");
        }
        assertThat(text(pdf)).contains("TOTAL (120 CT-es)", "120,00");
    }

    private String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
            return new PDFTextStripper().getText(document);
        }
    }

    private InactiveClientReport report(List<InactiveClientReport.Cte> ctes) {
        return new InactiveClientReport(26055L, 2025, "MANDARIM TINTAS RIO PRETO LOJA 11 LTDA", "BARATO TINTAS",
                "48108565000113", "SÃO JOSÉ DO RIO PRETO", "SP", ctes);
    }

    private InactiveClientReport.Cte cte(long number, String payment, String sender, String recipient,
            String invoices, String weight, String invoiceValue, String freight) {
        String client = "48108565000113";
        return new InactiveClientReport.Cte(number, LocalDate.of(2025, 3, 10), payment,
                sender, sender.startsWith("MANDARIM") ? client : "11222333000181", "SÃO PAULO",
                recipient, recipient.startsWith("MANDARIM") ? client : "44555666000199", "CEDRAL", invoices, invoices.isEmpty() ? 1 : invoices.split(",").length + 2,
                new BigDecimal(weight), new BigDecimal(invoiceValue),
                new BigDecimal(freight));
    }
}
