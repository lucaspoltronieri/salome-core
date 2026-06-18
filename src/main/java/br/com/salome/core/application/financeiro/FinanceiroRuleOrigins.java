package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.legacy.LegacyOrigin;

public final class FinanceiroRuleOrigins {

    public static final LegacyOrigin NOTA_COMPRA_DUPLICATA = LegacyOrigin.of(
            "salome-legacy/view/NotaCompraDuplicatas.java",
            "tabela de duplicatas e baixa em NotaCompraDuplicataBaixa",
            "salome-legacy/model/data/NotaCompraDuplicatasData.java",
            "notacompra, notacompraduplicatas, notacomprarateio");

    public static final LegacyOrigin PAGAMENTO_CAIXA = LegacyOrigin.of(
            "salome-legacy/view/PagamentoCaixa.java",
            "btnBaixarActionPerformed / verificaPermissaoAlteraCaixa",
            "salome-legacy/model/data/PagamentoCaixaData.java",
            "pagamentocaixa, caixa");

    public static final LegacyOrigin CAIXA_DINHEIRO = LegacyOrigin.of(
            "salome-legacy/view/PagamentoCaixa.java",
            "btnBaixarActionPerformed grava CaixaBean com tipoMovimento Saida e idPagamentoCaixa",
            "salome-legacy/model/data/CaixaData.java",
            "caixa, pagamentocaixa");

    public static final LegacyOrigin EXTRATO_AVULSO = LegacyOrigin.of(
            "salome-legacy/view/Extrato.java",
            "cadastro/consulta manual de extrato",
            "salome-legacy/model/data/ExtratoData.java",
            "extrato");

    public static final LegacyOrigin FATURA = LegacyOrigin.of(
            "salome-legacy/view/NotaServicoFatura.java",
            "Faturamento tela 65 / baixa com tipo Fatura ou Juros",
            "salome-legacy/model/data/FaturaData.java, salome-legacy/model/data/FaturaBaixaData.java",
            "fatura, faturabaixa, conhecimento, extrato");

    public static final LegacyOrigin CTE_ABERTO = LegacyOrigin.of(
            "salome-legacy/view/Conhecimento.java",
            "campos cteEmissao, idFatura, faturar, situacao, tipoPagamento",
            "salome-legacy/model/data/ConhecimentoData.java",
            "conhecimento, cliente");

    public static final LegacyOrigin CTE_EMITIDO = LegacyOrigin.of(
            "salome-legacy/view/Conhecimento.java",
            "regime competencia: receita pela data de emissao (cteEmissao), exclui cancelado/inutilizado/cortesia e fatura com baixa banco 34",
            "salome-legacy/model/data/ConhecimentoData.java",
            "conhecimento, cliente, faturabaixa, banco");

    public static final LegacyOrigin NOTA_COMPRA_COMPETENCIA = LegacyOrigin.of(
            "salome-legacy/view/RelatorioDespesasEntrada.java",
            "regime competencia: despesa pela dataEntrada com rateio temporal (valorNota/rateio por mes), exclui verRelatorioDespesas = Nao",
            "salome-legacy/model/data/NotaCompraData.java",
            "notacompra, notacomprarateio, notacompraprodutos");

    private FinanceiroRuleOrigins() {
    }
}
