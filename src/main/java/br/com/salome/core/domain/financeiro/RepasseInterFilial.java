package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Frete dos CT-es emitidos por uma filial ({@code origem}) mas entregues por outra ({@code destino}),
 * no periodo, agregado por par origem->destino. O destino vem da ultima perna de transferencia do
 * CT-e ({@code viagemtransferencia.idFilialDestino}). O DRE por filial aplica um percentual fixo sobre
 * esse frete para calcular o repasse que a emissora paga a entregadora (acerto inter-filial, zero-sum).
 */
public record RepasseInterFilial(
        Integer origem,
        Integer destino,
        BigDecimal frete
) {
}
