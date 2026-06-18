package br.com.salome.core.infrastructure.manifesto;

import br.com.salome.core.domain.manifesto.ManifestoBaixaExportRequest;
import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "salome.manifesto.export")
public record ManifestoBaixaExportProperties(
        boolean enabled,
        String spreadsheetId,
        String credentialsPath,
        Integer filialDestinoId,
        int batchSize,
        LocalDate dataCorte
) {

    public int effectiveBatchSize() {
        return batchSize <= 0 ? 500 : batchSize;
    }

    public LocalDate effectiveDataCorte() {
        return dataCorte == null ? ManifestoBaixaExportRequest.DEFAULT_DATA_CORTE : dataCorte;
    }
}
