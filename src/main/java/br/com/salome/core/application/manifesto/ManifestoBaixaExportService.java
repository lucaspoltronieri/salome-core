package br.com.salome.core.application.manifesto;

import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import br.com.salome.core.domain.manifesto.ManifestoBaixaCursor;
import br.com.salome.core.domain.manifesto.ManifestoBaixaExportRequest;
import br.com.salome.core.domain.manifesto.ManifestoBaixaExportResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManifestoBaixaExportService {

    private final ManifestoBaixaRepository repository;
    private final ManifestoBaixaSheetGateway sheetGateway;
    private final Clock clock;

    public ManifestoBaixaExportService(ManifestoBaixaRepository repository, ManifestoBaixaSheetGateway sheetGateway,
            Clock clock) {
        this.repository = repository;
        this.sheetGateway = sheetGateway;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ManifestoBaixaExportResult exportarPendentes(ManifestoBaixaExportRequest request) {
        Integer filialDestinoId = resolverFilialDestino(request.filialDestinoId());
        int limite = request.batchSize();
        LocalDate dataCorte = request.dataCorte();

        sheetGateway.garantirEstrutura();

        List<CteMapaSjpRecord> armazemSjp = repository.listarArmazemSjp(filialDestinoId, dataCorte, limite);
        List<CteMapaSjpRecord> emRotaEntrega = repository.listarEmRotaEntrega(filialDestinoId, dataCorte, limite);
        List<CteMapaSjpRecord> outrosArmazens = repository.listarOutrosArmazens(filialDestinoId, dataCorte, limite);
        List<CteMapaSjpRecord> viagensParaSjp = repository.listarViagensParaSjp(filialDestinoId, dataCorte, limite);

        sheetGateway.substituirMapaAtual(armazemSjp, emRotaEntrega, outrosArmazens, viagensParaSjp, clock.instant());

        int total = armazemSjp.size() + emRotaEntrega.size() + outrosArmazens.size() + viagensParaSjp.size();
        ManifestoBaixaCursor cursor = ManifestoBaixaCursor.inicial();
        return new ManifestoBaixaExportResult(total, total, 0, cursor, cursor);
    }

    private Integer resolverFilialDestino(Integer configuredFilialDestinoId) {
        if (configuredFilialDestinoId != null && configuredFilialDestinoId > 0) {
            return configuredFilialDestinoId;
        }
        return repository.buscarFilialDestinoSaoJoseRioPreto()
                .orElseThrow(() -> new IllegalStateException(
                        "Filial destino Sao Jose do Rio Preto nao encontrada no banco legado."));
    }
}
