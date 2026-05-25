package br.com.salome.core.domain.financeiro;

import br.com.salome.core.domain.notacompra.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FluxoCaixaPrevistoLancamento(
        Integer idNotaCompra,
        Integer idNotaCompraDuplicata,
        String documentoFiscal,
        String parcela,
        Integer idFilial,
        String filialNome,
        Integer idFornecedor,
        String fornecedorNome,
        Integer idBanco,
        String bancoNome,
        Integer idPlanoContasCentroCusto,
        String planoContasNome,
        LocalDate dataPrevista,
        LocalDate dataRealizada,
        BigDecimal valorPrevisto,
        BigDecimal valorRealizado,
        String historico,
        String meioPagamento,
        boolean realizado,
        FluxoCaixaPrevistoStatus status,
        String destinoDrillDown,
        LegacyOrigin origin
) {

    public BigDecimal valorProjetadoEfetivo() {
        return valorPrevisto == null ? BigDecimal.ZERO : valorPrevisto;
    }

    public BigDecimal valorRealizadoEfetivo() {
        return valorRealizado == null ? BigDecimal.ZERO : valorRealizado;
    }
}
