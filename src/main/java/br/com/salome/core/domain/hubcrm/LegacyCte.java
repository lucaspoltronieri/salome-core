package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;

/** CT-e emitido e não cancelado, com os totais das notas fiscais e a composição do frete. */
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
        BigDecimal totalFreight,
        Charges charges
) {
    /** Composição do frete do CT-e, nos mesmos campos da cotação ({@code null} quando não lida). */
    public record Charges(BigDecimal freightWeight, BigDecimal freightValue, BigDecimal toll, BigDecimal pickup,
            BigDecimal delivery, BigDecimal dispatch, BigDecimal gris, BigDecimal redelivery, BigDecimal icms,
            BigDecimal discount, BigDecimal addition) {}

    public LegacyCte(long id, String number, String series, String accessKey, LocalDate issueDate, String issueTime,
            String paymentType, String senderCnpj, String recipientCnpj, String payerCnpj, BigDecimal weight,
            BigDecimal invoiceValue, int volumes, BigDecimal totalFreight) {
        this(id, number, series, accessKey, issueDate, issueTime, paymentType, senderCnpj, recipientCnpj, payerCnpj,
                weight, invoiceValue, volumes, totalFreight, null);
    }

    public String label() {
        return series == null || series.isBlank() ? number : number + "/" + series;
    }
}
