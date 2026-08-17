package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;

public record QuoteIntegration(
        long legacyQuoteId,
        String payerCnpj,
        String legacyStatus,
        Long dealId,
        Long organizationId,
        Long peopleId,
        Long assignedUserId,
        BigDecimal totalFreight,
        String snapshotHash,
        String status,
        String whatsappStatus
) {}
