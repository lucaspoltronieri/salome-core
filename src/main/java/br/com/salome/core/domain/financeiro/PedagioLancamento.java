package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um lancamento de pedagio (vale-pedagio / tag) por placa de veiculo, vindo do extrato JSON enviado
 * pela operadora (ex.: Move Mais/AUTOBAN). {@code valor} ja vem com o sinal do pedagio: passagem
 * positiva, estorno de passagem negativo. A {@code data} e a data de ocorrencia (passagem na praca).
 * Esse custo NAO esta no bolo do legado (notacompra/caixa), entao entra como ajuste off-book no DRE.
 */
public record PedagioLancamento(
        String placa,
        LocalDate data,
        BigDecimal valor
) {
}
