package br.com.salome.core.domain.manifesto;

import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CteMapaSjpRecord(
        Integer idConhecimento,
        Integer cte,
        LocalDate dataEntradaArmazem,
        String horaEntradaArmazem,
        LocalDate dataEmissao,
        LocalDate dataPrevistaEntrega,
        String situacaoCte,
        String filialEmissao,
        String remetente,
        String destinatario,
        String cidadeDestinatario,
        String setorRegiao,
        String notasFiscais,
        BigDecimal quantidadeVolumes,
        BigDecimal peso,
        BigDecimal valorNf,
        BigDecimal valorTotalCte,
        String armazemAtual,
        Integer idManifestoTransferencia,
        Integer idViagem,
        String filialOrigem,
        LocalDate dataPrevisaoSaida,
        String horaPrevisaoSaida,
        String placaVeiculo,
        String motorista,
        LocalDate dataPrevisaoChegada,
        String horaPrevisaoChegada,
        LegacyOrigin origin
) {
}
