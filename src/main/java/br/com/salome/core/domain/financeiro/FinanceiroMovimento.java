package br.com.salome.core.domain.financeiro;

import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceiroMovimento(
        FinanceiroNatureza natureza,
        FinanceiroStatus status,
        FinanceiroOrigemTipo origemTipo,
        Integer origemId,
        LocalDate dataCompetencia,
        LocalDate dataVencimento,
        LocalDate dataBaixa,
        BigDecimal valor,
        Integer bancoId,
        String banco,
        Integer clienteFornecedorId,
        String clienteFornecedor,
        Integer centroCustoId,
        String centroCusto,
        Integer filialId,
        String filial,
        Integer planoContasCentroCustoId,
        String planoContas,
        String classificacao,
        String dmr,
        String documento,
        String historico,
        boolean tomadorExpressoSalome,
        boolean bancoPerdasDanos,
        LegacyOrigin origin
) {

    public FinanceiroMovimento {
        valor = valor == null ? BigDecimal.ZERO : valor;
        banco = branco(banco);
        clienteFornecedor = branco(clienteFornecedor);
        centroCusto = branco(centroCusto);
        filial = branco(filial);
        planoContas = branco(planoContas);
        classificacao = branco(classificacao);
        dmr = branco(dmr);
        documento = branco(documento);
        historico = branco(historico);
    }

    public LocalDate dataFluxo() {
        if (status == FinanceiroStatus.REALIZADO && dataBaixa != null) {
            return dataBaixa;
        }
        if (dataVencimento != null) {
            return dataVencimento;
        }
        return dataCompetencia;
    }

    public boolean receitaExcluida() {
        return natureza == FinanceiroNatureza.RECEITA && (tomadorExpressoSalome || bancoPerdasDanos);
    }

    /**
     * Despesa "caixa 2": duplicata de nota de compra cujo No. Duplicata foi cadastrado como "A VISTA".
     * Essas duplicatas nao representam um pagamento real e devem ser ignoradas no fluxo de caixa.
     * O documento e montado como {@code NC-<idNotaCompra>/<numeroDuplicata>}, entao o numero e o
     * trecho apos a primeira barra.
     */
    public boolean duplicataAVista() {
        if (origemTipo != FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA || documento == null) {
            return false;
        }
        int barra = documento.indexOf('/');
        String numero = barra >= 0 ? documento.substring(barra + 1) : documento;
        String normalizado = java.text.Normalizer.normalize(numero, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
        return normalizado.equals("A VISTA") || normalizado.equals("AVISTA");
    }

    private static String branco(String valor) {
        return valor == null || valor.isBlank() ? "Nao informado" : valor;
    }
}
