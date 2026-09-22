package br.com.salome.core.domain.hubcrm;

import java.time.LocalDate;

/**
 * Dados para imprimir a cotação no mesmo formato da tela do legado: os valores vêm
 * da {@link LegacyQuote} e os blocos de remetente, destinatário e consignatário
 * trazem o endereço completo, que a sincronização com o CRM não precisa.
 */
public record LegacyQuotePrint(
        LegacyQuote quote,
        Party sender,
        Party recipient,
        Party consignee,
        LocalDate expectedDelivery,
        String additionalInfo
) {
    public record Party(
            String cnpj,
            String name,
            String address,
            String complement,
            String district,
            String city,
            String zipCode,
            String phone,
            String email
    ) {}
}
