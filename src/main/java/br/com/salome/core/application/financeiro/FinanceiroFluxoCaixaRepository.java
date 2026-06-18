package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.DreClienteDriver;
import br.com.salome.core.domain.financeiro.DreFaturaCte;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.PlanoConta;
import java.math.BigDecimal;
import java.util.List;

public interface FinanceiroFluxoCaixaRepository {

    List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro);

    /**
     * Movimentos para o DRE gerencial em regime de competencia: receita pela data de emissao do
     * CT-e ({@code conhecimento.cteEmissao}) e despesa de nota de compra pela {@code dataEntrada}
     * com rateio temporal (valorNota/rateio por mes). Extrato, caixa (dinheiro) e pagamento caixa
     * permanecem como no fluxo de caixa. A data relevante para o periodo e {@code dataCompetencia}.
     */
    default List<FinanceiroMovimento> listarMovimentosCompetencia(FinanceiroFiltro filtro) {
        return List.of();
    }

    /**
     * Toneladas transportadas no periodo pela data de emissao do CT-e ({@code conhecimento.cteEmissao}),
     * somando {@code ConhecimentoNotasFiscais.pesoNf} (kg) e dividindo por 1000. Exclui CT-es cancelados/
     * inutilizados/cortesia e CT-es cuja fatura tenha baixa no banco 34 (Perdas e Danos). NAO e regime caixa:
     * o peso vem da emissao, e o custo (regime caixa) vem dos movimentos. Usado no card "Custo por peso
     * transportado" do DRE gerencial.
     */
    default BigDecimal somarToneladasTransportadas(FinanceiroFiltro filtro) {
        return BigDecimal.ZERO;
    }

    default List<FinanceiroSaldoBanco> listarSaldosBancarios(FinanceiroFiltro filtro) {
        return List.of();
    }

    /** Plano de contas legado (tabela {@code planocontas}) para rotular sinteticos e aplicar a flag impostosFinanceiro. */
    default List<PlanoConta> listarPlanoContas() {
        return List.of();
    }

    default boolean demonstrativo() {
        return false;
    }

    /**
     * Mapa placa (normalizada, sem hifen/espaco, maiuscula) -> idFilial do veiculo, da tabela legada
     * {@code veiculo}. Usado pelo DRE por filial para atribuir o pedagio (por placa) a cada filial.
     */
    default java.util.Map<String, Integer> listarFilialPorPlaca() {
        return java.util.Map.of();
    }

    /**
     * Frete por par (filial emissora -> filial entregadora) dos CT-es transferidos no periodo (pela
     * emissao), usando a ultima perna de {@code viagemtransferencia}. Usado pelo DRE por filial para o
     * acerto inter-filial (a emissora paga a entregadora um percentual fixo sobre o frete).
     */
    default java.util.List<br.com.salome.core.domain.financeiro.RepasseInterFilial> listarRepasseTransferencia(
            FinanceiroFiltro filtro) {
        return java.util.List.of();
    }

    /**
     * Toneladas transportadas por filial emissora ({@code conhecimento.idFilial}) no periodo, pela
     * emissao do CT-e. Usado pelo DRE por filial para apropriar o custo do centro de custo
     * TRANSFERENCIA entre as filiais por peso.
     */
    default java.util.Map<Integer, BigDecimal> listarPesoPorFilial(FinanceiroFiltro filtro) {
        return java.util.Map.of();
    }

    /**
     * Direcionadores de rateio por cliente (tomador do CT-e) no periodo, pela emissao
     * ({@code conhecimento.cteEmissao}): toneladas ({@code SUM(pesoNf)/1000}) e quantidade de CT-es.
     * Mesmas exclusoes de {@link #somarToneladasTransportadas} (cancelado/inutilizado/cortesia e
     * faturas baixadas no banco 34/Perdas e Danos). Usado pelo DRE por cliente para os criterios Peso
     * e Numero de CT-es.
     */
    default List<DreClienteDriver> listarDriversRateioPorCliente(FinanceiroFiltro filtro) {
        return List.of();
    }

    // Drill-down receita: Cliente -> Faturas -> CTEs
    default List<FinanceiroDrillNode> listarClientes(FinanceiroFiltro filtro) {
        return List.of();
    }

    default List<FinanceiroDrillNode> listarFaturasDoCliente(int idCliente, FinanceiroFiltro filtro) {
        return List.of();
    }

    default List<FinanceiroDrillNode> listarCtesDaFatura(int idFatura) {
        return List.of();
    }

    /** CT-es detalhados de uma fatura (remetente, destinatario, volume, peso, valor nota e frete) para o DRE gerencial. */
    default List<DreFaturaCte> listarCtesDetalhadosDaFatura(int idFatura) {
        return List.of();
    }

    // Drill-down despesa: Fornecedor -> Notas de compra -> Produtos
    default List<FinanceiroDrillNode> listarFornecedores(FinanceiroFiltro filtro) {
        return List.of();
    }

    default List<FinanceiroDrillNode> listarNotasDoFornecedor(int idFornecedor, FinanceiroFiltro filtro) {
        return List.of();
    }

    default List<FinanceiroDrillNode> listarProdutosDaNota(int idNotaCompra) {
        return List.of();
    }
}
