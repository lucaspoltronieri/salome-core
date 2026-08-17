package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record LegacyQuote(
        long id,
        LocalDate createdDate,
        String createdTime,
        String responsible,
        String status,
        LocalDateTime statusAt,
        String paymentType,
        String senderCnpj,
        String senderName,
        String senderCity,
        String recipientCnpj,
        String recipientName,
        String recipientCity,
        String payerCnpj,
        String payerName,
        String payerPhone,
        String payerEmail,
        String cargoType,
        int volumes,
        BigDecimal weight,
        BigDecimal invoiceValue,
        BigDecimal cubage,
        BigDecimal freightWeight,
        BigDecimal freightValue,
        BigDecimal toll,
        BigDecimal pickup,
        BigDecimal delivery,
        BigDecimal dispatch,
        BigDecimal gris,
        BigDecimal redelivery,
        BigDecimal icms,
        BigDecimal discount,
        BigDecimal addition,
        BigDecimal totalFreight,
        String approvalContact,
        Map<LossReason, String> selectedLossReasons
) {}
