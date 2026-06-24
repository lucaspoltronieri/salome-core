package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Caminhão (viagem) no mapa do armazém, com os CT-es agregados. Usado tanto
 * para "vindo de outras bases" (transferência) quanto para "saíram para entrega".
 */
public record MapaCaminhao(
        Long idViagem,
        String placa,
        String origem,
        String motorista,
        LocalDate dataPrevisaoSaida,
        String horaPrevisaoSaida,
        LocalDate dataPrevisaoChegada,
        String horaPrevisaoChegada,
        int qtdCtes,
        BigDecimal volumes,
        BigDecimal peso,
        List<MapaCte> ctes
) {
}
