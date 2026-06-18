package br.com.salome.core.domain.manifesto;

import java.time.LocalDate;

public record ManifestoBaixaExportRequest(
        Integer filialDestinoId,
        int batchSize,
        LocalDate dataCorte
) {

    public static final LocalDate DEFAULT_DATA_CORTE = LocalDate.of(2026, 5, 1);

    public ManifestoBaixaExportRequest(Integer filialDestinoId, int batchSize) {
        this(filialDestinoId, batchSize, DEFAULT_DATA_CORTE);
    }

    public ManifestoBaixaExportRequest {
        batchSize = batchSize <= 0 ? 500 : batchSize;
        dataCorte = dataCorte == null ? DEFAULT_DATA_CORTE : dataCorte;
    }
}
