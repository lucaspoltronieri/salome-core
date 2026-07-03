package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.AtividadeCarga;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.Carga;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.ParticipanteCarga;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * XLSX do relatório de produtividade por carga: uma aba "Cargas" (1 linha por caminhão,
 * com as três leituras de tempo em minutos) e uma aba "Detalhe" (atividade → pessoa).
 * Segue o padrão POI de {@code MapaArmazemExportService}.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class RelatorioCargasExportService {

    public byte[] exportarXlsx(RelatorioProdutividadeCargas rel) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle number = numberStyle(wb);
            CellStyle dateTime = dateTimeStyle(wb);
            abaCargas(wb, header, number, dateTime, rel);
            abaDetalhe(wb, header, dateTime, number, rel);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Erro ao gerar XLSX de produtividade por carga.", ex);
        }
    }

    private void abaCargas(Workbook wb, CellStyle header, CellStyle number, CellStyle dateTime,
                           RelatorioProdutividadeCargas rel) {
        Sheet sheet = novaAba(wb, "Cargas", header,
                "Placa", "Origem", "Motorista", "Tipo", "Início", "Fim",
                "Janela (min)", "Efetivo (min)", "Horas-homem (min)", "Operadores",
                "CT-es", "Volumes", "Peso (kg)", "Peso/h-h (kg/h)");
        int r = 1;
        for (Carga c : rel.cargas()) {
            Row row = sheet.createRow(r++);
            txt(row, 0, c.placa());
            txt(row, 1, c.origem());
            txt(row, 2, c.motorista());
            txt(row, 3, tipoRot(c.tipo()));
            dat(row, 4, c.inicio(), dateTime);
            dat(row, 5, c.fim(), dateTime);
            minutos(row, 6, c.janelaSeg(), number);
            minutos(row, 7, c.efetivoSeg(), number);
            minutos(row, 8, c.horasHomemSeg(), number);
            row.createCell(9).setCellValue(c.qtdOperadores());
            row.createCell(10).setCellValue(c.qtdCtes());
            row.createCell(11).setCellValue(c.volumes());
            num(row, 12, c.peso(), number);
            num(row, 13, pesoPorHora(c.peso(), c.horasHomemSeg()), number);
        }
        autosize(sheet, 14);
    }

    private void abaDetalhe(Workbook wb, CellStyle header, CellStyle dateTime, CellStyle number,
                            RelatorioProdutividadeCargas rel) {
        Sheet sheet = novaAba(wb, "Detalhe", header,
                "Placa", "Atividade", "Status", "Operador", "Entrada", "Saída", "Tempo (min)");
        int r = 1;
        for (Carga c : rel.cargas()) {
            for (AtividadeCarga a : c.atividades()) {
                for (ParticipanteCarga p : a.participantes()) {
                    Row row = sheet.createRow(r++);
                    txt(row, 0, c.placa());
                    row.createCell(1).setCellValue(a.idAtividade());
                    txt(row, 2, a.status());
                    txt(row, 3, p.nome());
                    dat(row, 4, p.entradaEm(), dateTime);
                    dat(row, 5, p.saidaEm(), dateTime);
                    minutos(row, 6, p.segundos(), number);
                }
            }
        }
        autosize(sheet, 7);
    }

    // ---- Helpers POI --------------------------------------------------------

    private static String tipoRot(String tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case "DESCARGA_TRANSFERENCIA" -> "Transferência";
            case "DESCARGA_COLETA" -> "Coleta";
            default -> tipo;
        };
    }

    private static BigDecimal pesoPorHora(BigDecimal peso, long horasHomemSeg) {
        if (peso == null || horasHomemSeg <= 0) {
            return null;
        }
        return peso.multiply(BigDecimal.valueOf(3600))
                .divide(BigDecimal.valueOf(horasHomemSeg), 1, java.math.RoundingMode.HALF_UP);
    }

    private Sheet novaAba(Workbook wb, String nome, CellStyle header, String... titulos) {
        Sheet sheet = wb.createSheet(nome);
        sheet.createFreezePane(0, 1);
        Row row = sheet.createRow(0);
        for (int i = 0; i < titulos.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titulos[i]);
            cell.setCellStyle(header);
        }
        return sheet;
    }

    private void autosize(Sheet sheet, int colunas) {
        for (int i = 0; i < colunas; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void txt(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private void num(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void minutos(Row row, int col, long segundos, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        cell.setCellValue(Math.round(segundos / 60.0));
    }

    private void dat(Row row, int col, Instant value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(Date.from(value));
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        var font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle dateTimeStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
        return style;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.0"));
        return style;
    }
}
