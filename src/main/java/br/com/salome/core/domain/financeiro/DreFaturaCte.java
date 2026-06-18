package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Detalhe de um CT-e que compoe uma fatura, usado no drill-down do DRE gerencial
 * (nivel abaixo do card FAT-). Read-only: espelha colunas do legado
 * {@code conhecimento} + agregacao de {@code ConhecimentoNotasFiscais}.
 */
public record DreFaturaCte(
        Integer cte,
        String remetente,
        String destinatario,
        Integer volume,
        BigDecimal peso,
        BigDecimal valorNota,
        BigDecimal valorFrete) {
}
