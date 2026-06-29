package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/**
 * Ocupação consolidada de um box/local do armazém: quantos documentos estão lá e
 * em que estágio (aguardando separação, em separação, prontos, carregando, avarias),
 * com os totais de volumes/peso. Alimenta os cards da tela "Armazém atual" e o
 * gráfico de ocupação por box do dashboard.
 *
 * <p>{@code idLocal}/{@code codigo} podem ser nulos para o agrupamento "sem box"
 * (documentos sem local resolvido, ex.: avaria registrada antes de endereçar).
 */
public record BoxOcupacao(
        Long idLocal,
        String codigo,
        String nome,
        String tipo,
        int total,
        int aguardandoSeparacao,
        int emSeparacao,
        int prontos,
        int emCarregamento,
        int avarias,
        int volumes,
        BigDecimal peso
) {
}
