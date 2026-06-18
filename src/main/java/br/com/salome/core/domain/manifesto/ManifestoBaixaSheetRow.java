package br.com.salome.core.domain.manifesto;

import java.util.ArrayList;
import java.util.List;

public record ManifestoBaixaSheetRow(List<Object> values, Integer idManifesto, Integer idConhecimento) {

    public ManifestoBaixaSheetRow {
        values = List.copyOf(values);
    }

    public String dedupeKey() {
        return idManifesto + ":" + idConhecimento;
    }

    public List<Object> baseValues(String situacaoCte) {
        List<Object> row = new ArrayList<>(values);
        ensureSize(row, 22);
        row.set(19, situacaoCte == null ? "" : situacaoCte);
        return row.subList(0, 22);
    }

    public List<Object> emViagemValues(ManifestoBaixaSituacaoAtual situacao) {
        List<Object> row = new ArrayList<>(baseValues(situacao.situacaoCte()));
        row.add(situacao.idViagemEntrega() == null ? "" : situacao.idViagemEntrega());
        row.add(situacao.placaVeiculo() == null ? "" : situacao.placaVeiculo());
        row.add(situacao.motorista() == null ? "" : situacao.motorista());
        return row;
    }

    private static void ensureSize(List<Object> row, int size) {
        while (row.size() < size) {
            row.add("");
        }
    }
}
