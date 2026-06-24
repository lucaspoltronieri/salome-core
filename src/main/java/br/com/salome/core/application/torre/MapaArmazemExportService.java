package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.MapaArmazemSnapshot;
import br.com.salome.core.domain.torre.MapaCaminhao;
import br.com.salome.core.domain.torre.MapaCte;
import br.com.salome.core.domain.torre.MapaFiltro;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Gera o XLSX do mapa do armazém: uma aba por seção, só as colunas "necessárias".
 * Aplica os mesmos filtros da tela para o arquivo refletir o que o operador vê.
 * Segue o padrão de {@code CteSemFaturaExportService} (POI).
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class MapaArmazemExportService {

    public byte[] exportarXlsx(MapaArmazemSnapshot snap, MapaFiltro filtro) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle date = dateStyle(wb);
            CellStyle number = numberStyle(wb);

            abaVindo(wb, header, date, number, snap.vindoDeOutrasBases(), filtro);
            abaAguardando(wb, header, number, snap.aguardandoDescarga(), filtro);
            abaDescarregando(wb, header, date, snap.descarregando(), snap.atualizadoEm(), filtro);
            abaArmazenado(wb, header, date, number, snap.armazenado(), filtro);
            abaEntrega(wb, header, date, number, snap.emRotaEntrega(), filtro);
            abaOutros(wb, header, number, snap.outrosArmazens(), filtro);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Erro ao gerar XLSX do mapa do armazém.", ex);
        }
    }

    // ---- Abas ---------------------------------------------------------------

    private void abaVindo(Workbook wb, CellStyle header, CellStyle date, CellStyle number,
                          List<MapaCaminhao> caminhoes, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "Vindo de outras bases", header,
                "Placa", "Origem", "Motorista", "Prev. saída", "Prev. chegada",
                "CT-e", "NFs", "Destinatário", "Cidade", "Setor/Região", "Volumes", "Peso");
        int r = 1;
        for (MapaCaminhao c : caminhoes) {
            for (MapaCte cte : c.ctes()) {
                if (!f.casaTexto(c.placa(), c.origem(), c.motorista(), texto(cte.cte()), cte.notasFiscais(),
                        cte.destinatario(), cte.cidadeDestinatario())
                        || !f.casaCidade(cte.cidadeDestinatario())) {
                    continue;
                }
                Row row = sheet.createRow(r++);
                txt(row, 0, c.placa());
                txt(row, 1, c.origem());
                txt(row, 2, c.motorista());
                dat(row, 3, c.dataPrevisaoSaida(), date);
                dat(row, 4, c.dataPrevisaoChegada(), date);
                intCell(row, 5, cte.cte());
                txt(row, 6, cte.notasFiscais());
                txt(row, 7, cte.destinatario());
                txt(row, 8, cte.cidadeDestinatario());
                txt(row, 9, cte.setorRegiao());
                num(row, 10, cte.volumes(), number);
                num(row, 11, cte.peso(), number);
            }
        }
        autosize(sheet, 12);
    }

    private void abaAguardando(Workbook wb, CellStyle header, CellStyle number,
                               List<ViagemAguardando> viagens, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "Aguardando descarga", header,
                "Placa", "Origem", "Data baixa", "Hora", "Qtd CT-es", "Volumes", "Peso");
        int r = 1;
        for (ViagemAguardando v : viagens) {
            if (!f.casaTexto(v.placa(), v.origem())) {
                continue;
            }
            Row row = sheet.createRow(r++);
            txt(row, 0, v.placa());
            txt(row, 1, v.origem());
            txt(row, 2, v.dataBaixa() == null ? null : v.dataBaixa().toString());
            txt(row, 3, v.horaBaixa());
            row.createCell(4).setCellValue(v.qtdCtes());
            num(row, 5, v.volumes(), number);
            num(row, 6, v.peso(), number);
        }
        autosize(sheet, 7);
    }

    private void abaDescarregando(Workbook wb, CellStyle header, CellStyle date,
                                  List<AtividadeResumo> atividades, Instant agora, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "Descarregando", header,
                "Viagem", "Placa", "Pessoas ativas", "Início", "Tempo (min)");
        int r = 1;
        for (AtividadeResumo a : atividades) {
            String viagem = a.idViagemLegado() == null ? null : a.idViagemLegado().toString();
            if (!f.casaTexto(viagem, a.placaVeiculo())) {
                continue;
            }
            Row row = sheet.createRow(r++);
            txt(row, 0, viagem);
            txt(row, 1, a.placaVeiculo());
            row.createCell(2).setCellValue(a.participantesAtivos());
            dat(row, 3, a.iniciadaEm() == null ? null : LocalDate.ofInstant(a.iniciadaEm(), java.time.ZoneId.systemDefault()), date);
            long min = a.iniciadaEm() == null ? 0 : Duration.between(a.iniciadaEm(), agora).toMinutes();
            row.createCell(4).setCellValue(min);
        }
        autosize(sheet, 5);
    }

    private void abaArmazenado(Workbook wb, CellStyle header, CellStyle date, CellStyle number,
                               List<MapaCte> ctes, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "No armazém", header,
                "CT-e", "Entrada", "Hora", "Destinatário", "Cidade", "Setor/Região",
                "Volumes", "Peso", "Situação", "Prev. entrega");
        int r = 1;
        for (MapaCte cte : ctes) {
            if (!casaCte(f, cte)) {
                continue;
            }
            Row row = sheet.createRow(r++);
            intCell(row, 0, cte.cte());
            dat(row, 1, cte.dataEntradaArmazem(), date);
            txt(row, 2, cte.horaEntradaArmazem());
            txt(row, 3, cte.destinatario());
            txt(row, 4, cte.cidadeDestinatario());
            txt(row, 5, cte.setorRegiao());
            num(row, 6, cte.volumes(), number);
            num(row, 7, cte.peso(), number);
            txt(row, 8, cte.situacaoCte());
            dat(row, 9, cte.dataPrevistaEntrega(), date);
        }
        autosize(sheet, 10);
    }

    private void abaEntrega(Workbook wb, CellStyle header, CellStyle date, CellStyle number,
                            List<MapaCaminhao> caminhoes, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "Saíram para entrega", header,
                "Placa", "Motorista", "Prev. saída", "Prev. entrega",
                "CT-e", "NFs", "Destinatário", "Cidade", "Setor/Região", "Volumes", "Peso");
        int r = 1;
        for (MapaCaminhao c : caminhoes) {
            for (MapaCte cte : c.ctes()) {
                if (!f.casaTexto(c.placa(), c.motorista(), texto(cte.cte()), cte.notasFiscais(),
                        cte.destinatario(), cte.cidadeDestinatario())
                        || !f.casaCidade(cte.cidadeDestinatario())) {
                    continue;
                }
                Row row = sheet.createRow(r++);
                txt(row, 0, c.placa());
                txt(row, 1, c.motorista());
                dat(row, 2, c.dataPrevisaoSaida(), date);
                dat(row, 3, cte.dataPrevistaEntrega(), date);
                intCell(row, 4, cte.cte());
                txt(row, 5, cte.notasFiscais());
                txt(row, 6, cte.destinatario());
                txt(row, 7, cte.cidadeDestinatario());
                txt(row, 8, cte.setorRegiao());
                num(row, 9, cte.volumes(), number);
                num(row, 10, cte.peso(), number);
            }
        }
        autosize(sheet, 11);
    }

    private void abaOutros(Workbook wb, CellStyle header, CellStyle number,
                           List<MapaCte> ctes, MapaFiltro f) {
        Sheet sheet = novaAba(wb, "Outros armazéns", header,
                "CT-e", "Armazém atual", "Destinatário", "Cidade", "Volumes", "Peso", "Situação");
        int r = 1;
        for (MapaCte cte : ctes) {
            if (!casaCte(f, cte)) {
                continue;
            }
            Row row = sheet.createRow(r++);
            intCell(row, 0, cte.cte());
            txt(row, 1, cte.armazemAtual());
            txt(row, 2, cte.destinatario());
            txt(row, 3, cte.cidadeDestinatario());
            num(row, 4, cte.volumes(), number);
            num(row, 5, cte.peso(), number);
            txt(row, 6, cte.situacaoCte());
        }
        autosize(sheet, 7);
    }

    private boolean casaCte(MapaFiltro f, MapaCte cte) {
        return f.casaTexto(texto(cte.cte()), cte.notasFiscais(), cte.remetente(),
                       cte.destinatario(), cte.cidadeDestinatario(), cte.placaVeiculo())
                && f.casaCidade(cte.cidadeDestinatario())
                && f.casaSituacao(cte.situacaoCte());
    }

    // ---- Helpers POI --------------------------------------------------------

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

    private void intCell(Row row, int col, Integer value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        } else {
            row.createCell(col);
        }
    }

    private void num(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void dat(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(Date.valueOf(value));
        }
    }

    private String texto(Integer value) {
        return value == null ? null : value.toString();
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

    private CellStyle dateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.0"));
        return style;
    }
}
