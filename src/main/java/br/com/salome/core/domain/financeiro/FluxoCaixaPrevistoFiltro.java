package br.com.salome.core.domain.financeiro;

import java.time.LocalDate;
import java.time.YearMonth;

public record FluxoCaixaPrevistoFiltro(
        LocalDate periodoInicio,
        LocalDate periodoFim,
        Integer horizonteDias,
        String filial,
        String fornecedor,
        String banco,
        String planoContas,
        FluxoCaixaPrevistoStatus status
) {

    private static final int HORIZONTE_PADRAO = 30;

    public static FluxoCaixaPrevistoFiltro padrao() {
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.from(hoje);
        return new FluxoCaixaPrevistoFiltro(
                mesAtual.atDay(1),
                mesAtual.atEndOfMonth(),
                HORIZONTE_PADRAO,
                null,
                null,
                null,
                null,
                FluxoCaixaPrevistoStatus.TODAS
        );
    }

    public FluxoCaixaPrevistoFiltro comPeriodo(LocalDate inicio, LocalDate fim) {
        return new FluxoCaixaPrevistoFiltro(inicio, fim, horizonteDias, filial, fornecedor, banco, planoContas, status);
    }

    public FluxoCaixaPrevistoFiltro comHorizonteDias(Integer valor) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, normalizeHorizonte(valor), filial, fornecedor,
                banco, planoContas, status);
    }

    public FluxoCaixaPrevistoFiltro comFilial(String valor) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, horizonteDias, normalize(valor), fornecedor,
                banco, planoContas, status);
    }

    public FluxoCaixaPrevistoFiltro comFornecedor(String valor) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, horizonteDias, filial, normalize(valor), banco,
                planoContas, status);
    }

    public FluxoCaixaPrevistoFiltro comBanco(String valor) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, horizonteDias, filial, fornecedor,
                normalize(valor), planoContas, status);
    }

    public FluxoCaixaPrevistoFiltro comPlanoContas(String valor) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, horizonteDias, filial, fornecedor, banco,
                normalize(valor), status);
    }

    public FluxoCaixaPrevistoFiltro comStatus(FluxoCaixaPrevistoStatus novoStatus) {
        return new FluxoCaixaPrevistoFiltro(periodoInicio, periodoFim, horizonteDias, filial, fornecedor, banco,
                planoContas, novoStatus == null ? FluxoCaixaPrevistoStatus.TODAS : novoStatus);
    }

    public boolean hasPeriodoValido() {
        return periodoInicio != null && periodoFim != null && !periodoFim.isBefore(periodoInicio);
    }

    public LocalDate periodoOperacionalFim() {
        int dias = horizonteDias == null ? HORIZONTE_PADRAO : Math.max(0, horizonteDias);
        return periodoFim == null ? null : periodoFim.plusDays(dias);
    }

    private Integer normalizeHorizonte(Integer valor) {
        return valor == null ? HORIZONTE_PADRAO : Math.max(0, valor);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
