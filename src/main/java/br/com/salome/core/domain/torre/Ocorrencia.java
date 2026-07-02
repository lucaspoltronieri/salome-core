package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ocorrência operacional. O caso principal é a AVARIA, que carrega o ciclo de
 * tratamento (status), culpa/responsáveis, valor total e o tempo que o operador
 * levou registrando no app; CT-es, fotos e itens ficam em tabelas filhas.
 */
public record Ocorrencia(
        Long id,
        int idFilial,
        String tipo,
        String status,
        Long idDocumento,
        Long idAtividade,
        String placaVeiculo,
        String motorista,
        Long idViagemLegado,
        String descricao,
        String culpa,
        String quemCausou,
        String responsavelPagamento,
        BigDecimal valorTotal,
        Instant dataIdentificacao,
        Integer duracaoSegundos,
        String fotoPath,
        Long idUsuario,
        Instant criadoEm,
        Long tratadaPor,
        Instant tratadaEm,
        String resolucao
) {
}
