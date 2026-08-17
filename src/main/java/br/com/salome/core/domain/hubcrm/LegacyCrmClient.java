package br.com.salome.core.domain.hubcrm;

import java.time.LocalDate;

public record LegacyCrmClient(
        long legacyClientId,
        String legalName,
        String cnpj,
        String city,
        String state,
        String email,
        String phone,
        String segment,
        String contactName,
        String contactDepartment,
        String contactEmail,
        String contactPhone,
        LocalDate firstCteWithoutFreight
) {}
