package br.com.salome.core.domain.manifesto;

import java.time.LocalDate;

public record ManifestoBaixaCursor(
        LocalDate dataBaixa,
        String horaBaixa,
        Integer idManifesto
) implements Comparable<ManifestoBaixaCursor> {

    public static ManifestoBaixaCursor inicial() {
        return new ManifestoBaixaCursor(LocalDate.now(), "00:00", 0);
    }

    public ManifestoBaixaCursor {
        horaBaixa = horaBaixa == null || horaBaixa.isBlank() ? "00:00" : horaBaixa;
        idManifesto = idManifesto == null ? 0 : idManifesto;
    }

    @Override
    public int compareTo(ManifestoBaixaCursor other) {
        int data = dataBaixa.compareTo(other.dataBaixa);
        if (data != 0) {
            return data;
        }
        int hora = horaBaixa.compareTo(other.horaBaixa);
        if (hora != 0) {
            return hora;
        }
        return idManifesto.compareTo(other.idManifesto);
    }
}
