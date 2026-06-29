package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha enxuta de CT-e no mapa do armazém (Torre operacional). Subconjunto
 * "necessário" de {@code CteMapaSjpRecord} — sem campos financeiros/internos.
 * Campos não aplicáveis a uma seção vêm nulos.
 */
public record MapaCte(
        Integer cte,
        LocalDate dataEmissao,
        String notasFiscais,
        String remetente,
        String destinatario,
        String cidadeDestinatario,
        String setorRegiao,
        BigDecimal volumes,
        BigDecimal peso,
        String situacaoCte,
        LocalDate dataEntradaArmazem,
        String horaEntradaArmazem,
        LocalDate dataPrevistaEntrega,
        String armazemAtual,
        Long idViagem,
        String placaVeiculo
) {
}
