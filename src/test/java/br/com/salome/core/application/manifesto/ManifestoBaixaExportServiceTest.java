package br.com.salome.core.application.manifesto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.salome.core.domain.legacy.LegacyOrigin;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import br.com.salome.core.domain.manifesto.ManifestoBaixaExportRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManifestoBaixaExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T13:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate DATA_CORTE = LocalDate.of(2026, 5, 1);

    @Test
    void shouldReplaceAllCurrentMapSheets() {
        FakeRepository repository = new FakeRepository();
        repository.armazemSjp = List.of(record(101, "Armazem"));
        repository.emRotaEntrega = List.of(deliveryRecord(102));
        repository.outrosArmazens = List.of(warehouseRecord(103, "EXPRESSO SALOME - CATANDUVA"));
        repository.viagensParaSjp = List.of(transferRecord(104));
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        var result = service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));

        assertEquals(4, result.consultados());
        assertEquals(4, result.exportados());
        assertEquals(List.of(101), sheet.armazemSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());
        assertEquals(List.of(102), sheet.emRotaEntrega.stream().map(CteMapaSjpRecord::idConhecimento).toList());
        assertEquals(List.of(103), sheet.outrosArmazens.stream().map(CteMapaSjpRecord::idConhecimento).toList());
        assertEquals(List.of(104), sheet.viagensParaSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());
        assertEquals(CLOCK.instant(), sheet.exportadoEm);
        assertEquals(DATA_CORTE, repository.lastDataCorte);
    }

    @Test
    void shouldResolveSaoJoseFilialWhenNotConfigured() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        service.exportarPendentes(new ManifestoBaixaExportRequest(null, 500));

        assertEquals(2, repository.lastFilialDestinoId);
    }

    @Test
    void shouldUseDefaultCutoffDateWhenRequestDoesNotProvideOne() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500, null));

        assertEquals(ManifestoBaixaExportRequest.DEFAULT_DATA_CORTE, repository.lastDataCorte);
    }

    @Test
    void shouldLeaveCtesBeforeCutoffDateOutOfOperationalSheets() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);
        repository.armazemSjp = List.of(record(106, "Armazem", LocalDate.of(2026, 4, 30)));

        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500, DATA_CORTE));

        assertTrue(sheet.armazemSjp.isEmpty());
    }

    @Test
    void shouldMoveCteFromOtherWarehouseToTransferTripWhenCurrentStateChanges() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        repository.outrosArmazens = List.of(warehouseRecord(103, "EXPRESSO SALOME - CATANDUVA"));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertEquals(List.of(103), sheet.outrosArmazens.stream().map(CteMapaSjpRecord::idConhecimento).toList());
        assertTrue(sheet.viagensParaSjp.isEmpty());

        repository.outrosArmazens = List.of();
        repository.viagensParaSjp = List.of(transferRecord(103));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertTrue(sheet.outrosArmazens.isEmpty());
        assertEquals(List.of(103), sheet.viagensParaSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());
    }

    @Test
    void shouldMoveCteFromTransferTripToSjpWarehouseWhenManifestoIsDownloaded() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        repository.viagensParaSjp = List.of(transferRecord(104));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertEquals(List.of(104), sheet.viagensParaSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());

        repository.viagensParaSjp = List.of();
        repository.armazemSjp = List.of(record(104, "Armazem"));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertTrue(sheet.viagensParaSjp.isEmpty());
        assertEquals(List.of(104), sheet.armazemSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());
    }

    @Test
    void shouldMoveCteFromSjpWarehouseToDeliveryRouteWhenItLeavesForDelivery() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        repository.armazemSjp = List.of(record(105, "Armazem"));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertEquals(List.of(105), sheet.armazemSjp.stream().map(CteMapaSjpRecord::idConhecimento).toList());

        repository.armazemSjp = List.of();
        repository.emRotaEntrega = List.of(deliveryRecord(105));
        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));
        assertTrue(sheet.armazemSjp.isEmpty());
        assertEquals(List.of(105), sheet.emRotaEntrega.stream().map(CteMapaSjpRecord::idConhecimento).toList());
    }

    @Test
    void shouldLeaveFinalizedOrCanceledCtesOutOfOperationalSheets() {
        FakeRepository repository = new FakeRepository();
        FakeSheetGateway sheet = new FakeSheetGateway();
        ManifestoBaixaExportService service = new ManifestoBaixaExportService(repository, sheet, CLOCK);

        service.exportarPendentes(new ManifestoBaixaExportRequest(2, 500));

        assertTrue(sheet.armazemSjp.isEmpty());
        assertTrue(sheet.emRotaEntrega.isEmpty());
        assertTrue(sheet.outrosArmazens.isEmpty());
        assertTrue(sheet.viagensParaSjp.isEmpty());
    }

    @Test
    void repositoryContractShouldExposeReadOnlyMethodsOnly() {
        for (Method method : ManifestoBaixaRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertTrue(!name.startsWith("save"));
            assertTrue(!name.startsWith("update"));
            assertTrue(!name.startsWith("delete"));
            assertTrue(!name.startsWith("create"));
            assertTrue(!name.startsWith("insert"));
        }
    }

    private static CteMapaSjpRecord record(Integer conhecimento, String situacao) {
        return record(conhecimento, situacao, LocalDate.of(2026, 5, 27));
    }

    private static CteMapaSjpRecord record(Integer conhecimento, String situacao, LocalDate dataEmissao) {
        return new CteMapaSjpRecord(
                conhecimento,
                377020,
                LocalDate.of(2026, 5, 27),
                "08:30",
                dataEmissao,
                LocalDate.of(2026, 6, 3),
                situacao,
                "EXPRESSO SALOME - SAO JOSE DO RIO PRETO",
                "AKZO NOBEL LTDA - 60561719002258",
                "CLIENTE TESTE LTDA - 00000000000000",
                "SAO JOSE DO RIO PRETO",
                "GERAL ( SAO JOSE DO RIO PRETO )",
                "123,456",
                new BigDecimal("12"),
                new BigDecimal("129.194"),
                new BigDecimal("1000.00"),
                new BigDecimal("120.00"),
                "EXPRESSO SALOME - SAO JOSE DO RIO PRETO",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LegacyOrigin.of("test", "test", "test", "test")
        );
    }

    private static CteMapaSjpRecord warehouseRecord(Integer conhecimento, String armazemAtual) {
        CteMapaSjpRecord record = record(conhecimento, "Armazem");
        return new CteMapaSjpRecord(
                record.idConhecimento(), record.cte(), record.dataEntradaArmazem(), record.horaEntradaArmazem(),
                record.dataEmissao(), record.dataPrevistaEntrega(), record.situacaoCte(), record.filialEmissao(),
                record.remetente(),
                record.destinatario(),
                record.cidadeDestinatario(), record.setorRegiao(), record.notasFiscais(),
                record.quantidadeVolumes(), record.peso(), record.valorNf(), record.valorTotalCte(), armazemAtual,
                record.idManifestoTransferencia(), record.idViagem(), record.filialOrigem(),
                record.dataPrevisaoSaida(), record.horaPrevisaoSaida(), record.placaVeiculo(), record.motorista(),
                record.dataPrevisaoChegada(), record.horaPrevisaoChegada(), record.origin()
        );
    }

    private static CteMapaSjpRecord deliveryRecord(Integer conhecimento) {
        CteMapaSjpRecord record = record(conhecimento, "Em Viagem");
        return tripRecord(record, 9001, 168265, null);
    }

    private static CteMapaSjpRecord transferRecord(Integer conhecimento) {
        CteMapaSjpRecord record = record(conhecimento, "Em Viagem");
        return tripRecord(record, 7001, 168265, "EXPRESSO SALOME - CATANDUVA");
    }

    private static CteMapaSjpRecord tripRecord(CteMapaSjpRecord record, Integer manifesto, Integer viagem,
            String filialOrigem) {
        return new CteMapaSjpRecord(
                record.idConhecimento(), record.cte(), record.dataEntradaArmazem(), record.horaEntradaArmazem(),
                record.dataEmissao(), record.dataPrevistaEntrega(), record.situacaoCte(), record.filialEmissao(),
                record.remetente(),
                record.destinatario(), record.cidadeDestinatario(), record.setorRegiao(), record.notasFiscais(),
                record.quantidadeVolumes(), record.peso(), record.valorNf(), record.valorTotalCte(),
                record.armazemAtual(), manifesto, viagem, filialOrigem, LocalDate.of(2026, 5, 28), "08:00",
                "ABC1D23", "JOAO MOTORISTA", LocalDate.of(2026, 5, 28), "14:00", record.origin()
        );
    }

    private static final class FakeRepository implements ManifestoBaixaRepository {
        private List<CteMapaSjpRecord> armazemSjp = List.of();
        private List<CteMapaSjpRecord> emRotaEntrega = List.of();
        private List<CteMapaSjpRecord> outrosArmazens = List.of();
        private List<CteMapaSjpRecord> viagensParaSjp = List.of();
        private Integer lastFilialDestinoId;
        private LocalDate lastDataCorte;

        @Override
        public Optional<Integer> buscarFilialDestinoSaoJoseRioPreto() {
            return Optional.of(2);
        }

        @Override
        public List<CteMapaSjpRecord> listarArmazemSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
            lastFilialDestinoId = filialDestinoId;
            lastDataCorte = dataCorte;
            return armazemSjp.stream()
                    .filter(record -> !record.dataEmissao().isBefore(dataCorte))
                    .limit(limite)
                    .toList();
        }

        @Override
        public List<CteMapaSjpRecord> listarEmRotaEntrega(Integer filialDestinoId, LocalDate dataCorte, int limite) {
            lastFilialDestinoId = filialDestinoId;
            lastDataCorte = dataCorte;
            return emRotaEntrega.stream()
                    .filter(record -> !record.dataEmissao().isBefore(dataCorte))
                    .limit(limite)
                    .toList();
        }

        @Override
        public List<CteMapaSjpRecord> listarOutrosArmazens(Integer filialDestinoId, LocalDate dataCorte, int limite) {
            lastFilialDestinoId = filialDestinoId;
            lastDataCorte = dataCorte;
            return outrosArmazens.stream()
                    .filter(record -> !record.dataEmissao().isBefore(dataCorte))
                    .limit(limite)
                    .toList();
        }

        @Override
        public List<CteMapaSjpRecord> listarViagensParaSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
            lastFilialDestinoId = filialDestinoId;
            lastDataCorte = dataCorte;
            return viagensParaSjp.stream()
                    .filter(record -> !record.dataEmissao().isBefore(dataCorte))
                    .limit(limite)
                    .toList();
        }
    }

    private static final class FakeSheetGateway implements ManifestoBaixaSheetGateway {
        private List<CteMapaSjpRecord> armazemSjp = List.of();
        private List<CteMapaSjpRecord> emRotaEntrega = List.of();
        private List<CteMapaSjpRecord> outrosArmazens = List.of();
        private List<CteMapaSjpRecord> viagensParaSjp = List.of();
        private Instant exportadoEm;

        @Override
        public void garantirEstrutura() {
        }

        @Override
        public void substituirMapaAtual(
                List<CteMapaSjpRecord> armazemSjp,
                List<CteMapaSjpRecord> emRotaEntrega,
                List<CteMapaSjpRecord> outrosArmazens,
                List<CteMapaSjpRecord> viagensParaSjp,
                Instant exportadoEm) {
            this.armazemSjp = List.copyOf(armazemSjp);
            this.emRotaEntrega = List.copyOf(emRotaEntrega);
            this.outrosArmazens = List.copyOf(outrosArmazens);
            this.viagensParaSjp = List.copyOf(viagensParaSjp);
            this.exportadoEm = exportadoEm;
        }
    }
}
