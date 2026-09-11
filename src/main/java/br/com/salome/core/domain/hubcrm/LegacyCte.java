package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;

/** CT-e emitido e não cancelado, com os totais das notas fiscais. */
public record LegacyCte(
        long id,
        String number,
        String series,
        String accessKey,
        LocalDate issueDate,
        String issueTime,
        String paymentType,
        String senderCnpj,
        String recipientCnpj,
        String payerCnpj,
        BigDecimal weight,
        BigDecimal invoiceValue,
        int volumes,
        BigDecimal totalFreight
) {
    public String label() {
        return series == null || series.isBlank() ? number : number + "/" + series;
    }
}
