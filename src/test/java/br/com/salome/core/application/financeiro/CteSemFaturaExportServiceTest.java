package br.com.salome.core.application.financeiro;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.salome.core.domain.financeiro.CteSemFaturaExportRow;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class CteSemFaturaExportServiceTest {

    @Test
    void shouldExportHeadersInExactOrderAndFormatDates() throws Exception {
        CteSemFaturaExportService service = new CteSemFaturaExportService(ate -> List.of(
                new CteSemFaturaExportRow(377020, LocalDate.of(2026, 5, 31), "Remetente A",
                        "Destinatario B", "Finalizada", new BigDecimal("1234.56"), LocalDate.of(2026, 6, 2))));

        byte[] bytes = service.exportarXlsx(LocalDate.of(2026, 5, 31));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("CTes sem fatura");
            var header = sheet.getRow(0);
            String[] headers = new String[CteSemFaturaExportService.HEADERS.length];
            for (int i = 0; i < headers.length; i++) {
                headers[i] = header.getCell(i).getStringCellValue();
            }
            assertArrayEquals(CteSemFaturaExportService.HEADERS, headers);

            var row = sheet.getRow(1);
            assertEquals(377020, (int) row.getCell(0).getNumericCellValue());
            assertEquals("Remetente A", row.getCell(2).getStringCellValue());
            assertEquals("Destinatario B", row.getCell(3).getStringCellValue());
            assertEquals("Finalizada", row.getCell(4).getStringCellValue());
            assertEquals(1234.56, row.getCell(5).getNumericCellValue(), 0.001);
            assertEquals("#,##0.00", row.getCell(5).getCellStyle().getDataFormatString());
            assertEquals(LocalDate.of(2026, 5, 31),
                    DateUtil.getLocalDateTime(row.getCell(1).getNumericCellValue()).toLocalDate());
            assertEquals("dd/mm/yyyy", row.getCell(1).getCellStyle().getDataFormatString());
            assertEquals(LocalDate.of(2026, 6, 2),
                    DateUtil.getLocalDateTime(row.getCell(6).getNumericCellValue()).toLocalDate());
            assertEquals("dd/mm/yyyy", row.getCell(6).getCellStyle().getDataFormatString());
        }
    }
}
