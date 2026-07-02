package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/**
 * Tratamento de uma ocorrência pela web (atualização parcial estilo PATCH).
 * Campos nulos não são alterados; {@code status} move o ciclo quando informado.
 */
public record TratarOcorrenciaRequest(
        String status,
        String culpa,
        String quemCausou,
        String responsavelPagamento,
        BigDecimal valorTotal,
        String resolucao
) {
}
