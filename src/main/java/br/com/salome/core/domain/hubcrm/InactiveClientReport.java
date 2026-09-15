package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CT-es de um cliente para o comercial prospectar: os de um ano em que ele foi o tomador do frete
 * (cliente inativo, estágio Pagantes) ou os que ele recebeu com o frete pago pelo remetente
 * (estágio Não Pagantes). {@code period} é o que aparece no título do PDF ("2025", "recebidos").
 */
public record InactiveClientReport(
        long clientId,
        int year,
        String period,
        String name,
        String tradeName,
        String cnpj,
        String city,
        String state,
        List<Cte> ctes
) {
    public InactiveClientReport(long clientId, int year, String name, String tradeName, String cnpj,
            String city, String state, List<Cte> ctes) {
        this(clientId, year, String.valueOf(year), name, tradeName, cnpj, city, state, ctes);
    }

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
