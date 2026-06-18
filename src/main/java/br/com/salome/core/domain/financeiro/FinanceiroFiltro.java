package br.com.salome.core.domain.financeiro;

import java.time.LocalDate;

public record FinanceiroFiltro(
        LocalDate inicio,
        LocalDate fim,
        String busca,
        String status,
        String natureza
) {

    public static FinanceiroFiltro padrao() {
        LocalDate hoje = LocalDate.now();
        return new FinanceiroFiltro(hoje.withDayOfMonth(1), hoje.plusMonths(2).withDayOfMonth(1).minusDays(1), "", "TODOS",
                "TODAS");
    }

    public FinanceiroFiltro normalizado() {
        FinanceiroFiltro padrao = padrao();
        LocalDate inicioNormalizado = inicio == null ? padrao.inicio() : inicio;
        LocalDate fimNormalizado = fim == null ? padrao.fim() : fim;
        return new FinanceiroFiltro(inicioNormalizado, fimNormalizado, texto(busca), texto(status, "TODOS"),
                texto(natureza, "TODAS"));
    }

    private String texto(String valor) {
        return texto(valor, "");
    }

    private String texto(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
