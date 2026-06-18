package br.com.salome.core.infrastructure.google;

import br.com.salome.core.application.manifesto.ManifestoBaixaSheetGateway;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import br.com.salome.core.infrastructure.manifesto.ManifestoBaixaExportProperties;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.BooleanRule;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.ConditionalFormatRule;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "salome.manifesto.export", name = "enabled", havingValue = "true")
public class GoogleSheetsManifestoBaixaGateway implements ManifestoBaixaSheetGateway {

    private static final String OLD_ARMAZEM_SHEET = "Baixas Manifesto";
    private static final String OLD_EM_ROTA_SHEET = "Em Viagem";
    private static final String ARMAZEM_SJP_SHEET = "Armaz\u00e9m SJP";
    private static final String EM_ROTA_ENTREGA_SHEET = "Em rota Entrega";
    private static final String OUTROS_ARMAZENS_SHEET = "CTe outros armaz\u00e9m";
    private static final String VIAGEM_PARA_SJP_SHEET = "CTe Viagem p SJP";
    private static final String CONTROL_SHEET = "_controle_manifestos";
    private static final List<String> COMMON_HEADERS = List.of(
            "exportado_em",
            "id_cte",
            "cte",
            "data_emissao",
            "previsao_entrega",
            "situacao_cte",
            "filial_emissao",
            "remetente",
            "destinatario",
            "cidade_destinatario",
            "setor_regiao",
            "notas_fiscais",
            "quantidade_volumes",
            "peso",
            "valor_nf",
            "valor_total_cte"
    );
    private static final List<String> ARMAZEM_SJP_HEADERS = concat(
            insertBeforeCte(COMMON_HEADERS, List.of("Data entrada armaz\u00e9m", "Hora entrada armaz\u00e9m")),
            List.of("armazem_atual")
    );
    private static final List<String> ARMAZEM_HEADERS = concat(COMMON_HEADERS, List.of("armazem_atual"));
    private static final List<String> EM_ROTA_HEADERS = concat(COMMON_HEADERS, List.of(
            "id_viagem_entrega",
            "id_viagem",
            "placa_veiculo",
            "motorista",
            "data_previsao_saida",
            "hora_previsao_saida",
            "data_previsao_chegada",
            "hora_previsao_chegada"
    ));
    private static final List<String> VIAGEM_HEADERS = concat(COMMON_HEADERS, List.of(
            "id_manifesto_transferencia",
            "id_viagem",
            "filial_origem",
            "data_previsao_saida",
            "hora_previsao_saida",
            "placa_veiculo",
            "motorista",
            "data_previsao_chegada",
            "hora_previsao_chegada"
    ));
    private static final List<String> CONTROL_HEADERS = List.of("tipo", "data_baixa", "hora_baixa", "id_manifesto",
            "chave");

    private final ManifestoBaixaExportProperties properties;
    private Sheets sheets;

    public GoogleSheetsManifestoBaixaGateway(ManifestoBaixaExportProperties properties) {
        this.properties = properties;
    }

    @Override
    public void garantirEstrutura() {
        try {
            Map<String, SheetProperties> sheetProperties = carregarSheetProperties();
            List<Request> requests = new ArrayList<>();

            renameIfNeeded(sheetProperties, OLD_ARMAZEM_SHEET, ARMAZEM_SJP_SHEET, requests);
            renameIfNeeded(sheetProperties, OLD_EM_ROTA_SHEET, EM_ROTA_ENTREGA_SHEET, requests);
            hideDuplicateOldSheet(sheetProperties, OLD_ARMAZEM_SHEET, ARMAZEM_SJP_SHEET, requests);
            hideDuplicateOldSheet(sheetProperties, OLD_EM_ROTA_SHEET, EM_ROTA_ENTREGA_SHEET, requests);
            if (!requests.isEmpty()) {
                batchUpdate(requests);
                sheetProperties = carregarSheetProperties();
                requests = new ArrayList<>();
            }

            ensureSheet(sheetProperties, ARMAZEM_SJP_SHEET, false, requests);
            ensureSheet(sheetProperties, EM_ROTA_ENTREGA_SHEET, false, requests);
            ensureSheet(sheetProperties, OUTROS_ARMAZENS_SHEET, false, requests);
            ensureSheet(sheetProperties, VIAGEM_PARA_SJP_SHEET, false, requests);
            ensureSheet(sheetProperties, CONTROL_SHEET, true, requests);
            if (!requests.isEmpty()) {
                batchUpdate(requests);
            }

            sheetProperties = carregarSheetProperties();
            SheetProperties control = sheetProperties.get(CONTROL_SHEET);
            if (control != null && !Boolean.TRUE.equals(control.getHidden())) {
                batchUpdate(List.of(hideSheet(control.getSheetId())));
            }

            ensureHeader(ARMAZEM_SJP_SHEET, ARMAZEM_SJP_HEADERS);
            ensureHeader(EM_ROTA_ENTREGA_SHEET, EM_ROTA_HEADERS);
            ensureHeader(OUTROS_ARMAZENS_SHEET, ARMAZEM_HEADERS);
            ensureHeader(VIAGEM_PARA_SJP_SHEET, VIAGEM_HEADERS);
            ensureHeader(CONTROL_SHEET, CONTROL_HEADERS);
            formatarColunasNumericas(carregarSheetProperties());
        } catch (IOException ex) {
            throw new UncheckedIOException("Nao foi possivel preparar a Google Sheet de mapa de CT-es SJP.", ex);
        }
    }

    @Override
    public void substituirMapaAtual(
            List<CteMapaSjpRecord> armazemSjp,
            List<CteMapaSjpRecord> emRotaEntrega,
            List<CteMapaSjpRecord> outrosArmazens,
            List<CteMapaSjpRecord> viagensParaSjp,
            Instant exportadoEm) {
        try {
            substituirAba(ARMAZEM_SJP_SHEET, ARMAZEM_SJP_HEADERS, armazemSjp.stream()
                    .map(record -> toSjpWarehouseRow(record, exportadoEm))
                    .toList());
            substituirAba(EM_ROTA_ENTREGA_SHEET, EM_ROTA_HEADERS, emRotaEntrega.stream()
                    .map(record -> toDeliveryRow(record, exportadoEm))
                    .toList());
            substituirAba(OUTROS_ARMAZENS_SHEET, ARMAZEM_HEADERS, outrosArmazens.stream()
                    .map(record -> toWarehouseRow(record, exportadoEm))
                    .toList());
            substituirAba(VIAGEM_PARA_SJP_SHEET, VIAGEM_HEADERS, viagensParaSjp.stream()
                    .map(record -> toTransferRow(record, exportadoEm))
                    .toList());
        } catch (IOException ex) {
            throw new UncheckedIOException("Nao foi possivel substituir o mapa atual de CT-es SJP.", ex);
        }
    }

    private void substituirAba(String sheet, List<String> headers, List<List<Object>> rows) throws IOException {
        clear(quoted(sheet) + "!A2:Z");
        if (!rows.isEmpty()) {
            updateValues(quoted(sheet) + "!A2:" + columnName(headers.size()), rows);
        }
    }

    private List<Object> toWarehouseRow(CteMapaSjpRecord record, Instant exportadoEm) {
        List<Object> row = new ArrayList<>(baseRow(record, exportadoEm));
        row.add(string(record.armazemAtual()));
        return row;
    }

    private List<Object> toSjpWarehouseRow(CteMapaSjpRecord record, Instant exportadoEm) {
        List<Object> row = new ArrayList<>(baseRowWithWarehouseEntry(record, exportadoEm));
        row.add(string(record.armazemAtual()));
        return row;
    }

    private List<Object> toDeliveryRow(CteMapaSjpRecord record, Instant exportadoEm) {
        List<Object> row = new ArrayList<>(baseRow(record, exportadoEm));
        row.add(nullable(record.idManifestoTransferencia()));
        row.add(nullable(record.idViagem()));
        row.add(string(record.placaVeiculo()));
        row.add(string(record.motorista()));
        row.add(string(record.dataPrevisaoSaida()));
        row.add(string(record.horaPrevisaoSaida()));
        row.add(string(record.dataPrevisaoChegada()));
        row.add(string(record.horaPrevisaoChegada()));
        return row;
    }

    private List<Object> toTransferRow(CteMapaSjpRecord record, Instant exportadoEm) {
        List<Object> row = new ArrayList<>(baseRow(record, exportadoEm));
        row.add(nullable(record.idManifestoTransferencia()));
        row.add(nullable(record.idViagem()));
        row.add(string(record.filialOrigem()));
        row.add(string(record.dataPrevisaoSaida()));
        row.add(string(record.horaPrevisaoSaida()));
        row.add(string(record.placaVeiculo()));
        row.add(string(record.motorista()));
        row.add(string(record.dataPrevisaoChegada()));
        row.add(string(record.horaPrevisaoChegada()));
        return row;
    }

    private List<Object> baseRow(CteMapaSjpRecord record, Instant exportadoEm) {
        return List.of(
                exportadoEm.toString(),
                nullable(record.idConhecimento()),
                text(record.cte()),
                string(record.dataEmissao()),
                string(record.dataPrevistaEntrega()),
                string(record.situacaoCte()),
                string(record.filialEmissao()),
                string(record.remetente()),
                string(record.destinatario()),
                string(record.cidadeDestinatario()),
                string(record.setorRegiao()),
                string(record.notasFiscais()),
                decimal(record.quantidadeVolumes()),
                decimal(record.peso()),
                decimal(record.valorNf()),
                decimal(record.valorTotalCte())
        );
    }

    private List<Object> baseRowWithWarehouseEntry(CteMapaSjpRecord record, Instant exportadoEm) {
        List<Object> row = new ArrayList<>();
        row.add(exportadoEm.toString());
        row.add(nullable(record.idConhecimento()));
        row.add(string(record.dataEntradaArmazem()));
        row.add(string(record.horaEntradaArmazem()));
        row.add(text(record.cte()));
        row.add(string(record.dataEmissao()));
        row.add(string(record.dataPrevistaEntrega()));
        row.add(string(record.situacaoCte()));
        row.add(string(record.filialEmissao()));
        row.add(string(record.remetente()));
        row.add(string(record.destinatario()));
        row.add(string(record.cidadeDestinatario()));
        row.add(string(record.setorRegiao()));
        row.add(string(record.notasFiscais()));
        row.add(decimal(record.quantidadeVolumes()));
        row.add(decimal(record.peso()));
        row.add(decimal(record.valorNf()));
        row.add(decimal(record.valorTotalCte()));
        return row;
    }

    private Sheets service() {
        if (sheets == null) {
            sheets = buildService();
        }
        return sheets;
    }

    private Sheets buildService() {
        try (var credentialsStream = Files.newInputStream(Path.of(properties.credentialsPath()))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS));
            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            ).setApplicationName("salome-core").build();
        } catch (IOException ex) {
            throw new UncheckedIOException("Nao foi possivel ler as credenciais do Google Sheets.", ex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Nao foi possivel inicializar transporte seguro para Google Sheets.", ex);
        }
    }

    private Map<String, SheetProperties> carregarSheetProperties() throws IOException {
        Spreadsheet spreadsheet = service().spreadsheets().get(properties.spreadsheetId()).execute();
        return spreadsheet.getSheets().stream()
                .map(sheet -> sheet.getProperties())
                .collect(Collectors.toMap(SheetProperties::getTitle, Function.identity()));
    }

    private void renameIfNeeded(Map<String, SheetProperties> sheetProperties, String oldTitle, String newTitle,
            List<Request> requests) {
        if (sheetProperties.containsKey(oldTitle) && !sheetProperties.containsKey(newTitle)) {
            requests.add(renameSheet(sheetProperties.get(oldTitle).getSheetId(), newTitle));
        }
    }

    private void hideDuplicateOldSheet(Map<String, SheetProperties> sheetProperties, String oldTitle, String newTitle,
            List<Request> requests) {
        if (sheetProperties.containsKey(oldTitle) && sheetProperties.containsKey(newTitle)
                && !Boolean.TRUE.equals(sheetProperties.get(oldTitle).getHidden())) {
            requests.add(hideSheet(sheetProperties.get(oldTitle).getSheetId()));
        }
    }

    private void ensureSheet(Map<String, SheetProperties> sheetProperties, String title, boolean hidden,
            List<Request> requests) {
        if (!sheetProperties.containsKey(title)) {
            requests.add(addSheet(title, hidden));
        }
    }

    private Request addSheet(String title, boolean hidden) {
        return new Request().setAddSheet(new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(title).setHidden(hidden)));
    }

    private Request renameSheet(Integer sheetId, String title) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setTitle(title))
                .setFields("title"));
    }

    private Request hideSheet(Integer sheetId) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setHidden(true))
                .setFields("hidden"));
    }

    private void batchUpdate(List<Request> requests) throws IOException {
        service().spreadsheets().batchUpdate(properties.spreadsheetId(),
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }

    private void ensureHeader(String sheet, List<String> headers) throws IOException {
        String headerRange = quoted(sheet) + "!A1:" + columnName(headers.size()) + "1";
        clear(quoted(sheet) + "!A1:Z1");
        service().spreadsheets().values()
                .update(properties.spreadsheetId(), headerRange,
                        new ValueRange().setValues(List.of(new ArrayList<>(headers))))
                .setValueInputOption("RAW")
                .execute();
    }

    private void clear(String range) throws IOException {
        service().spreadsheets().values().clear(properties.spreadsheetId(), range, new ClearValuesRequest()).execute();
    }

    private void updateValues(String range, List<List<Object>> rows) throws IOException {
        service().spreadsheets().values()
                .update(properties.spreadsheetId(), range, new ValueRange().setValues(rows))
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    private String quoted(String sheet) {
        return "'" + sheet.replace("'", "''") + "'";
    }

    private String columnName(int columnNumber) {
        StringBuilder name = new StringBuilder();
        int value = columnNumber;
        while (value > 0) {
            value--;
            name.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return name.toString();
    }

    private Object decimal(BigDecimal value) {
        return value == null ? "" : value.doubleValue();
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private void formatarColunasNumericas(Map<String, SheetProperties> sheetProperties) throws IOException {
        List<Request> requests = new ArrayList<>();
        addNumberFormats(sheetProperties.get(ARMAZEM_SJP_SHEET), 14, 15, 16, 17, requests);
        addNumberFormats(sheetProperties.get(EM_ROTA_ENTREGA_SHEET), 12, 13, 14, 15, requests);
        addNumberFormats(sheetProperties.get(OUTROS_ARMAZENS_SHEET), 12, 13, 14, 15, requests);
        addNumberFormats(sheetProperties.get(VIAGEM_PARA_SJP_SHEET), 12, 13, 14, 15, requests);
        addPlainTextFormat(sheetProperties.get(ARMAZEM_SJP_SHEET), 4, requests);
        addPlainTextFormat(sheetProperties.get(EM_ROTA_ENTREGA_SHEET), 2, requests);
        addPlainTextFormat(sheetProperties.get(OUTROS_ARMAZENS_SHEET), 2, requests);
        addPlainTextFormat(sheetProperties.get(VIAGEM_PARA_SJP_SHEET), 2, requests);
        addDateFormat(sheetProperties.get(ARMAZEM_SJP_SHEET), 2, requests);
        addDateFormat(sheetProperties.get(ARMAZEM_SJP_SHEET), 5, requests);
        addDateFormat(sheetProperties.get(ARMAZEM_SJP_SHEET), 6, requests);
        addDateFormat(sheetProperties.get(EM_ROTA_ENTREGA_SHEET), 3, requests);
        addDateFormat(sheetProperties.get(EM_ROTA_ENTREGA_SHEET), 4, requests);
        addDateFormat(sheetProperties.get(OUTROS_ARMAZENS_SHEET), 3, requests);
        addDateFormat(sheetProperties.get(OUTROS_ARMAZENS_SHEET), 4, requests);
        addDateFormat(sheetProperties.get(VIAGEM_PARA_SJP_SHEET), 3, requests);
        addDateFormat(sheetProperties.get(VIAGEM_PARA_SJP_SHEET), 4, requests);
        addDeliveryForecastRules(sheetProperties.get(ARMAZEM_SJP_SHEET), 6, requests);
        addDeliveryForecastRules(sheetProperties.get(EM_ROTA_ENTREGA_SHEET), 4, requests);
        addDeliveryForecastRules(sheetProperties.get(OUTROS_ARMAZENS_SHEET), 4, requests);
        addDeliveryForecastRules(sheetProperties.get(VIAGEM_PARA_SJP_SHEET), 4, requests);
        if (!requests.isEmpty()) {
            batchUpdate(requests);
        }
    }

    private void addNumberFormats(SheetProperties sheet, int volumesColumn, int pesoColumn, int valorNfColumn,
            int valorCteColumn, List<Request> requests) {
        if (sheet == null) {
            return;
        }
        int sheetId = sheet.getSheetId();
        requests.add(numberFormat(sheetId, volumesColumn, "NUMBER", "0"));
        requests.add(numberFormat(sheetId, pesoColumn, "NUMBER", "0.000"));
        requests.add(numberFormat(sheetId, valorNfColumn, "CURRENCY", "R$ #,##0.00"));
        requests.add(numberFormat(sheetId, valorCteColumn, "CURRENCY", "R$ #,##0.00"));
    }

    private Request numberFormat(Integer sheetId, int columnIndex, String type, String pattern) {
        return new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setStartColumnIndex(columnIndex)
                        .setEndColumnIndex(columnIndex + 1))
                .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                        .setNumberFormat(new NumberFormat().setType(type).setPattern(pattern))))
                .setFields("userEnteredFormat.numberFormat"));
    }

    private void addPlainTextFormat(SheetProperties sheet, int columnIndex, List<Request> requests) {
        if (sheet == null) {
            return;
        }
        requests.add(numberFormat(sheet.getSheetId(), columnIndex, "TEXT", "@"));
    }

    private void addDateFormat(SheetProperties sheet, int columnIndex, List<Request> requests) {
        if (sheet == null) {
            return;
        }
        requests.add(numberFormat(sheet.getSheetId(), columnIndex, "DATE", "yyyy-mm-dd"));
    }

    private void addDeliveryForecastRules(SheetProperties sheet, int columnIndex, List<Request> requests) {
        if (sheet == null) {
            return;
        }
        int sheetId = sheet.getSheetId();
        String column = columnName(columnIndex + 1);
        requests.add(conditionalFormat(sheetId, columnIndex, "=$" + column + "2<TODAY()",
                new Color().setRed(0.96f).setGreen(0.80f).setBlue(0.80f)));
        requests.add(conditionalFormat(sheetId, columnIndex, "=$" + column + "2=TODAY()",
                new Color().setRed(1.0f).setGreen(0.93f).setBlue(0.55f)));
    }

    private Request conditionalFormat(Integer sheetId, int columnIndex, String formula, Color background) {
        return new Request().setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                .setIndex(0)
                .setRule(new ConditionalFormatRule()
                        .setRanges(List.of(new GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(1)
                                .setStartColumnIndex(columnIndex)
                                .setEndColumnIndex(columnIndex + 1)))
                        .setBooleanRule(new BooleanRule()
                                .setCondition(new BooleanCondition()
                                        .setType("CUSTOM_FORMULA")
                                        .setValues(List.of(new ConditionValue().setUserEnteredValue(formula))))
                                .setFormat(new CellFormat().setBackgroundColor(background)))));
    }

    private Object text(Object value) {
        return value == null ? "" : "'" + value;
    }

    private static List<String> insertBeforeCte(List<String> headers, List<String> newHeaders) {
        List<String> result = new ArrayList<>(headers);
        int cteIndex = result.indexOf("cte");
        result.addAll(cteIndex, newHeaders);
        return List.copyOf(result);
    }
}
