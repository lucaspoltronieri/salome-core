package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;

/**
 * Direcionador de rateio por cliente (tomador do CT-e), apurado pela emissao no periodo:
 * toneladas transportadas ({@code SUM(pesoNf)/1000}) e quantidade de CT-es. Usado pelo DRE por
 * cliente para distribuir o bolo de despesas (regime caixa) entre os clientes pelo criterio Peso ou
 * Numero de CT-es. O criterio Valor de frete usa a propria receita realizada, sem este direcionador.
 */
public record DreClienteDriver(
        Integer idCliente,
        String nome,
        BigDecimal toneladas,
        int qtdCtes
) {

    public DreClienteDriver {
        toneladas = toneladas == null ? BigDecimal.ZERO : toneladas;
    }
}
