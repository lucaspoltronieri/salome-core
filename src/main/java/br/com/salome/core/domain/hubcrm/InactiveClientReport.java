package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CT-es de um ano em que o cliente foi o tomador do frete (Emitente CIF ou Destinatário FOB),
 * para o comercial prospectar clientes que pararam de transportar.
 */
public record InactiveClientReport(
        long clientId,
        int year,
        String name,
        String tradeName,
        String cnpj,
        String city,
        String state,
        List<Cte> ctes
) {
    public record Cte(
            long number,
            LocalDate issued,
            String paymentType,
            String sender,
            String senderCnpj,
            String senderCity,
            String recipient,
            String recipientCnpj,
            String recipientCity,
            String invoices,
            long volumes,
            BigDecimal weight,
            BigDecimal invoiceValue,
            BigDecimal freight
    ) {}

    public long totalVolumes() {
        return ctes.stream().mapToLong(Cte::volumes).sum();
    }

    public BigDecimal totalWeight() {
        return ctes.stream().map(Cte::weight).reduce(BigDecimal.ZERO, InactiveClientReport::sum);
    }

    public BigDecimal totalInvoiceValue() {
        return ctes.stream().map(Cte::invoiceValue).reduce(BigDecimal.ZERO, InactiveClientReport::sum);
    }

    public BigDecimal totalFreight() {
        return ctes.stream().map(Cte::freight).reduce(BigDecimal.ZERO, InactiveClientReport::sum);
    }

    private static BigDecimal sum(BigDecimal total, BigDecimal value) {
        return value == null ? total : total.add(value);
    }
}
