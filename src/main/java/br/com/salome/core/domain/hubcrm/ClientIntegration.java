package br.com.salome.core.domain.hubcrm;

import java.time.LocalDate;

public record ClientIntegration(
        long legacyClientId,
        String cnpj,
        Long organizationId,
        Long peopleId,
        Long dealId,
        Long assignedUserId,
        LocalDate firstCteWithoutFreight,
        String snapshotHash,
        String status
) {}
