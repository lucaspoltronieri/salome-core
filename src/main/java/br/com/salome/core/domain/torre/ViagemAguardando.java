package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Viagem de transferência baixada (chegada) e destinada à filial, ainda sem
 * descarga aberta na Torre. Agregada do legado (somente leitura).
 */
public record ViagemAguardando(
        long idViagemTransferencia,
        Long idViagem,
        LocalDate dataBaixa,
        String horaBaixa,
        String placa,
        String motorista,
        String origem,
        int qtdCtes,
        BigDecimal volumes,
        BigDecimal peso
) {
}
