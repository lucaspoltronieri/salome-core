package br.com.salome.core.domain.hubcrm;

/**
 * Corrente da cotação no legado: a coleta lançada na aprovação e o CT-e que saiu no retorno dela
 * ({@code conhecimento.idColeta}). Uma cotação pode ter mais de uma coleta, e uma coleta mais de um
 * CT-e; aqui vem uma linha por coleta, com o CT-e mais recente.
 *
 * <p>{@code cteId} nulo = coleta ainda sem CT-e.
 */
public record LegacyQuoteChain(long quoteId, LegacyColeta coleta, Long cteId) {

    public long coletaId() {
        return coleta == null ? 0 : coleta.id();
    }

    public boolean aliveColeta() {
        return coleta != null && coleta.alive();
    }
}
