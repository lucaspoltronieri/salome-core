package br.com.salome.core.domain.torre;

import java.time.Instant;

/**
 * Registro imutável de uma ação sensível (finalizar, cancelar, ajustar...).
 * Espelha a tabela {@code evento_auditoria}.
 */
public record EventoAuditoria(
        long id,
        Integer idFilial,
        Long idUsuario,
        String acao,
        String entidade,
        Long idEntidade,
        String detalhe,
        Instant ocorridoEm
) {
}
