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
        Map<LossReason, String> selectedLossReasons,
        PayerProfile payerProfile
) {
    public LegacyQuote {
        payerProfile = payerProfile == null ? PayerProfile.EMPTY : payerProfile;
    }

    /** Cotação sem os dados do cadastro do tomador (usado quando eles não importam). */
    public LegacyQuote(long id, LocalDate createdDate, String createdTime, String responsible, String status,
            LocalDateTime statusAt, String paymentType, String senderCnpj, String senderName, String senderCity,
            String recipientCnpj, String recipientName, String recipientCity, String payerCnpj, String payerName,
            String payerPhone, String payerEmail, String cargoType, int volumes, BigDecimal weight,
            BigDecimal invoiceValue, BigDecimal cubage, BigDecimal freightWeight, BigDecimal freightValue,
            BigDecimal toll, BigDecimal pickup, BigDecimal delivery, BigDecimal dispatch, BigDecimal gris,
            BigDecimal redelivery, BigDecimal icms, BigDecimal discount, BigDecimal addition,
            BigDecimal totalFreight, String approvalContact, Map<LossReason, String> selectedLossReasons) {
        this(id, createdDate, createdTime, responsible, status, statusAt, paymentType, senderCnpj, senderName,
                senderCity, recipientCnpj, recipientName, recipientCity, payerCnpj, payerName, payerPhone,
                payerEmail, cargoType, volumes, weight, invoiceValue, cubage, freightWeight, freightValue, toll,
                pickup, delivery, dispatch, gris, redelivery, icms, discount, addition, totalFreight,
                approvalContact, selectedLossReasons, PayerProfile.EMPTY);
    }

    /**
     * Dados do tomador do frete para os campos personalizados do card: segmento, cidade,
     * UF, telefone e e-mail vêm do cadastro do cliente no legado; a previsão de fechamento,
     * da própria cotação (quando informada).
     */
    public record PayerProfile(String segment, String city, String state, String phone, String email,
            LocalDate closingForecast) {
        public static final PayerProfile EMPTY = new PayerProfile(null, null, null, null, null, null);
    }
}
