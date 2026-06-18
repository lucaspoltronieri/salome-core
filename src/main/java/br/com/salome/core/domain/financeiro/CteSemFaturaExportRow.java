package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CteSemFaturaExportRow(
        Integer numeroCte,
        LocalDate dataEmissao,
        String remetente,
        String destinatario,
        String statusCte,
        BigDecimal totalFrete,
        LocalDate dataEntrega
) {
}
