package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Coleta do legado (tabela {@code coleta}). A partir da mudança do legado de 09/2026, aprovar a
 * cotação com {@code cotacao.coleta='Sim'} lança a coleta já com {@code coleta.idCotacao}
 * preenchido — é o primeiro elo da corrente cotação → coleta → CT-e
 * ({@code conhecimento.idColeta}).
 *
 * <p>{@code status} é o enum do legado: PENDENTE, EM VIAGEM, REALIZADA, CANCELADA.
 */
public record LegacyColeta(long id, long quoteId, String status, LocalDate data, LocalDate pickedUpAt,
        BigDecimal cubage, BigDecimal totalFreight) {

    /** Coleta em andamento: ainda pode virar CT-e, então segura a triagem da cotação sem CT-e. */
    public boolean alive() {
        String value = HubCrmNormalization.normalizedText(status);
        return "PENDENTE".equals(value) || "EM VIAGEM".equals(value);
    }
}
