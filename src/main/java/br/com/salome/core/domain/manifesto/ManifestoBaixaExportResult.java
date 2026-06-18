package br.com.salome.core.domain.manifesto;

public record ManifestoBaixaExportResult(
        int consultados,
        int exportados,
        int duplicadosIgnorados,
        ManifestoBaixaCursor cursorAnterior,
        ManifestoBaixaCursor cursorAtual
) {
}
