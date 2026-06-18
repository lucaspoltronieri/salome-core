package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.CteSemFaturaExportRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class CteSemFaturaExportService {

    public static final String[] HEADERS = {
            "Numero do CTe",
            "Data Emissão",
            "Remetente",
            "Destinatário",
            "Status CTe",
            "Total de Frete",
            "Data Entrega"
    };

    private final CteSemFaturaRepository repository;

    public CteSemFaturaExportService(CteSemFaturaRepository repository) {
        this.repository = repository;
    }

    public byte[] exportarXlsx(LocalDate ate) {
        List<CteSemFaturaExportRow> linhas = repository.listarEmitidosSemFaturaAte(ate);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("CTes sem fatura");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle moneyStyle = moneyStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (CteSemFaturaExportRow linha : linhas) {
                Row row = sheet.createRow(rowIndex++);
                setInteger(row.createCell(0), linha.numeroCte());
                setDate(row.createCell(1), linha.dataEmissao(), dateStyle);
                row.createCell(2).setCellValue(nullToBlank(linha.remetente()));
                row.createCell(3).setCellValue(nullToBlank(linha.destinatario()));
                row.createCell(4).setCellValue(nullToBlank(linha.statusCte()));
                setMoney(row.createCell(5), linha.totalFrete(), moneyStyle);
                setDate(row.createCell(6), linha.dataEntrega(), dateStyle);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Erro ao gerar XLSX de CT-es sem fatura.", ex);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void setInteger(Cell cell, Integer value) {
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private void setDate(Cell cell, LocalDate value, CellStyle style) {
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(Date.valueOf(value));
        }
    }

    private void setMoney(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
