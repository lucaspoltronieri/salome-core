package br.com.salome.core.infrastructure.legacy.financeiro;

import br.com.salome.core.application.financeiro.FinanceiroFluxoCaixaRepository;
import br.com.salome.core.application.financeiro.FinanceiroRuleOrigins;
import br.com.salome.core.domain.financeiro.DreClienteDriver;
import br.com.salome.core.domain.financeiro.DreFaturaCte;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class InMemoryFinanceiroFluxoCaixaRepository implements FinanceiroFluxoCaixaRepository {

    @Override
    public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
        LocalDate abril = LocalDate.of(2026, 4, 1);
        LocalDate maio = LocalDate.of(2026, 5, 1);
        LocalDate junho = LocalDate.of(2026, 6, 1);
        return List.of(
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.FATURA_BAIXA,
                        65021, abril.plusDays(1), abril.plusDays(1), abril.plusDays(1), "185240.30", 1, "Itau 341 - Operacional",
                        1203, "Akzo Nobel Ltda", 7, "Operacional", 1, "SPO", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "FAT-65021", "Baixa de fatura por retorno", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.PREVISTO, FinanceiroOrigemTipo.CTE_ABERTO,
                        377020, abril.plusDays(4), abril.plusDays(14), null, "42180.90", 1, "Itau 341 - Operacional",
                        1308, "Sherwin Williams", 7, "Operacional", 2, "SJP", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "CT-e 377020", "CT-e emitido ainda nao faturado", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.FATURA_BAIXA,
                        65034, abril.plusDays(2), abril.plusDays(2), abril.plusDays(2), "25000.00", 34, "34 - Perdas e danos",
                        999, "Cliente sinistro", 7, "Operacional", 1, "SPO", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "FAT-65034", "Exemplo removido pelo banco 34", false, true),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.PREVISTO, FinanceiroOrigemTipo.CTE_ABERTO,
                        377100, abril.plusDays(3), abril.plusDays(15), null, "18000.00", 1, "Itau 341 - Operacional",
                        1, "Expresso Salome", 7, "Operacional", 1, "SPO", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "CT-e 377100", "Exemplo removido por tomador Expresso Salome", true, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                        88410, abril.plusDays(1), abril.plusDays(6), abril.plusDays(6), "67320.20", 2, "Bradesco - Pagamentos",
                        702, "Fornecedor combustiveis", 4, "Frota", 2, "SJP", 22, "Combustiveis", "2.08.001",
                        "Custos frota", "NC-88410/1", "Duplicata de nota compra baixada", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.PREVISTO, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                        88411, abril.plusDays(2), abril.plusDays(19), null, "31500.00", 2, "Bradesco - Pagamentos",
                        820, "Oficina parceira", 4, "Frota", 3, "CAT", 30, "Manutencao preventiva", "2.08.012",
                        "Manutencao", "NC-88411/2", "Duplicata em aberto", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.EXTRATO_AVULSO,
                        99001, abril.plusDays(5), abril.plusDays(5), abril.plusDays(5), "2840.77", 1, "Itau 341 - Operacional",
                        null, "Banco Itau", 1, "Administrativo", null, null, 41, "Tarifas bancarias", "2.05.001",
                        "Despesas financeiras", "EXT-99001", "Tarifa bancaria e juros", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.CAIXA_DINHEIRO,
                        227001, abril.plusDays(7), abril.plusDays(7), abril.plusDays(7), "960.00", 9, "Caixa dinheiro",
                        533, "Fornecedor local", 1, "Administrativo", null, null, 52, "Despesas de caixa", "2.03.001",
                        "Funcionamento", "CX-227001", "Pagamento caixa tela 227", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.FATURA_BAIXA,
                        65110, maio.plusDays(2), maio.plusDays(2), maio.plusDays(2), "302704.34", 37, "Itau 41430-4",
                        1308, "Sherwin Williams", 7, "Operacional", 3, "CAM", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "FAT-65110", "Baixa de fatura por retorno", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                        88520, maio.plusDays(5), maio.plusDays(10), maio.plusDays(10), "88410.55", 36, "Sicredi",
                        721, "Fornecedor de pneus", 4, "Frota", 4, "RIB", 25, "Pneus", "2.08.004",
                        "Custos frota", "NC-88520/1", "Duplicata de nota compra baixada", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.EXTRATO_AVULSO,
                        99044, maio.plusDays(13), maio.plusDays(13), maio.plusDays(13), "1290.40", 40, "Santander",
                        null, "Banco Santander", 1, "Administrativo", null, null, 41, "Tarifas bancarias", "2.05.002",
                        "Despesas financeiras", "EXT-99044", "Tarifa bancaria", false, false),
                movimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.FATURA_BAIXA,
                        65240, junho.plusDays(3), junho.plusDays(3), junho.plusDays(3), "421880.90", 18, "Itau 00153-8",
                        1410, "Eucatex", 7, "Operacional", 1, "SPO", 18, "Receita de fretes", "1.01.001",
                        "Receita operacional", "FAT-65240", "Baixa de fatura por retorno", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.CAIXA_DINHEIRO,
                        227120, junho.plusDays(8), junho.plusDays(8), junho.plusDays(8), "6420.00", 9, "Caixa dinheiro",
                        533, "Fornecedor local", 1, "Administrativo", 1, "SPO", 52, "Despesas de caixa", "2.03.002",
                        "Funcionamento", "CX-227120", "Pagamento caixa tela 227", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                        88900, junho.plusDays(5), junho.plusDays(9), junho.plusDays(9), "12750.00", 36, "Sicredi",
                        910, "Fornecedor sem rateio", null, null, 1, "SPO", null, null, null,
                        null, "NC-88900/1", "Nota compra sem rateio e sem produto", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.EXTRATO_AVULSO,
                        99120, junho.plusDays(6), junho.plusDays(6), junho.plusDays(6), "318.90", 41, "Sicredi",
                        null, "Banco Sicredi", null, null, 1, "SPO", null, null, null,
                        null, "EXT-99120", "Tarifa bancaria sem plano", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                        90010, junho.plusDays(4), junho.plusDays(7), junho.plusDays(7), "9870.00", 36, "Sicredi",
                        640, "Receita Federal", 8, "Tributos", 1, "SPO", 61, "Impostos federais", "2.10.001",
                        "Impostos sobre servicos", "NC-90010/1", "Guia de imposto", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.PAGAMENTO_CAIXA,
                        34128, maio.plusDays(17), maio.plusDays(17), maio.plusDays(17), "50.00", 24, "Caixa CAM",
                        3056, "Jose Carlos Calca Christovam", 2, "Operacional", 3, "CAM", 90, "Manuseio de cargas", "2.09.001",
                        "Custos operacionais com carga", "PCX-34128 / 6037", "Descarga RE 167744 - Jose", false, false),
                movimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO, FinanceiroOrigemTipo.PAGAMENTO_CAIXA,
                        34250, junho.plusDays(6), junho.plusDays(6), junho.plusDays(6), "180.00", 24, "Caixa CAM",
                        3056, "Jose Carlos Calca Christovam", 2, "Operacional", 3, "CAM", 90, "Manuseio de cargas", "2.09.001",
                        "Custos operacionais com carga", "PCX-34250 / 6100", "Descarga manuseio - dinheiro", false, false)
        );
    }

    @Override
    public List<FinanceiroMovimento> listarMovimentosCompetencia(FinanceiroFiltro filtro) {
        // Modo demonstrativo: reaproveita o mesmo dataset de exemplo para o seletor de regime
        // funcionar em dev. Os movimentos ja trazem dataCompetencia, usada pelo servico de competencia.
        return listarMovimentos(filtro);
    }

    @Override
    public List<PlanoConta> listarPlanoContas() {
        return List.of(
                new PlanoConta("1", "RECEITAS", true, false),
                new PlanoConta("1.01", "RECEITA OPERACIONAL", true, false),
                new PlanoConta("1.01.001", "Receita de fretes", false, false),
                new PlanoConta("2", "DESPESAS", true, false),
                new PlanoConta("2.03", "DESPESAS DE FUNCIONAMENTO", true, false),
                new PlanoConta("2.03.001", "Despesas de caixa", false, false),
                new PlanoConta("2.03.002", "Material de escritorio", false, false),
                new PlanoConta("2.05", "DESPESAS FINANCEIRAS", true, true),
                new PlanoConta("2.05.001", "Tarifas bancarias", false, true),
                new PlanoConta("2.05.002", "Juros e IOF", false, true),
                new PlanoConta("2.08", "CUSTOS OPERACIONAIS COM FROTA", true, false),
                new PlanoConta("2.08.001", "Combustiveis e Arla", false, false),
                new PlanoConta("2.08.004", "Pneus e recapagem", false, false),
                new PlanoConta("2.08.012", "Manutencao de frota", false, false),
                new PlanoConta("2.09", "CUSTOS OPERACIONAIS COM CARGA", true, false),
                new PlanoConta("2.09.001", "Manuseio de cargas", false, false),
                new PlanoConta("2.10", "DEDUCOES DE VENDAS", true, true),
                new PlanoConta("2.10.001", "Impostos sobre servicos", false, true));
    }

    @Override
    public List<FinanceiroSaldoBanco> listarSaldosBancarios(FinanceiroFiltro filtro) {
        return List.of(
                new FinanceiroSaldoBanco(1, "Itau 341 - Operacional", new BigDecimal("245810.44"),
                        new BigDecimal("245810.44"), new BigDecimal("50000.00"), false),
                new FinanceiroSaldoBanco(2, "Bradesco - Pagamentos", new BigDecimal("84120.18"),
                        new BigDecimal("84120.18"), BigDecimal.ZERO, false),
                new FinanceiroSaldoBanco(9, "Caixa dinheiro", new BigDecimal("5280.00"), new BigDecimal("5280.00"),
                        BigDecimal.ZERO, true)
        );
    }

    @Override
    public boolean demonstrativo() {
        return true;
    }

    @Override
    public java.util.Map<String, Integer> listarFilialPorPlaca() {
        // Demo: algumas placas do extrato de pedagio de exemplo mapeadas a filiais ficticias.
        return java.util.Map.of("CCU5F57", 1, "STB5A20", 3, "DFJ1E38", 4);
    }

    @Override
    public java.util.List<br.com.salome.core.domain.financeiro.RepasseInterFilial> listarRepasseTransferencia(
            FinanceiroFiltro filtro) {
        // Demo: CT-es emitidos em SPO (1) e entregues por CAM (3) e RIB (4).
        return java.util.List.of(
                new br.com.salome.core.domain.financeiro.RepasseInterFilial(1, 3, new BigDecimal("80000.00")),
                new br.com.salome.core.domain.financeiro.RepasseInterFilial(1, 4, new BigDecimal("45000.00")),
                new br.com.salome.core.domain.financeiro.RepasseInterFilial(2, 1, new BigDecimal("20000.00")));
    }

    @Override
    public java.util.Map<Integer, BigDecimal> listarPesoPorFilial(FinanceiroFiltro filtro) {
        // Demo: toneladas ficticias por filial para apropriar o custo de transferencia por peso.
        return java.util.Map.of(1, new BigDecimal("420.00"), 2, new BigDecimal("180.00"),
                3, new BigDecimal("260.00"), 4, new BigDecimal("90.00"));
    }

    @Override
    public List<DreClienteDriver> listarDriversRateioPorCliente(FinanceiroFiltro filtro) {
        // Toneladas e CT-es ficticios coerentes com os clientes do dataset de receita (1203, 1308, 1410),
        // com R$/tonelada bem diferentes para o rateio por Peso divergir do rateio por Valor de frete.
        // O cliente 1599 nao tem receita no dataset: serve para demonstrar a parcela "nao atribuida".
        return List.of(
                new DreClienteDriver(1203, "Akzo Nobel Ltda", new BigDecimal("120.00"), 18),
                new DreClienteDriver(1308, "Sherwin Williams", new BigDecimal("260.00"), 32),
                new DreClienteDriver(1410, "Eucatex", new BigDecimal("95.00"), 12),
                new DreClienteDriver(1599, "Tinta Forte (sem fatura no periodo)", new BigDecimal("40.00"), 6));
    }

    @Override
    public List<FinanceiroDrillNode> listarClientes(FinanceiroFiltro filtro) {
        return List.of(
                new FinanceiroDrillNode("1203", "Akzo Nobel Ltda", "2 fatura(s)", new BigDecimal("210240.30"), 2, true),
                new FinanceiroDrillNode("1308", "Sherwin Williams", "1 fatura(s)", new BigDecimal("42180.90"), 1, true));
    }

    @Override
    public List<FinanceiroDrillNode> listarFaturasDoCliente(int idCliente, FinanceiroFiltro filtro) {
        return List.of(
                new FinanceiroDrillNode("FAT65021", "FAT-65021", "Venc 05/06/2026 - baixada", new BigDecimal("185240.30"), 2, true),
                new FinanceiroDrillNode("FAT65030", "FAT-65030", "Venc 18/06/2026 - em aberto", new BigDecimal("25000.00"), 1, true));
    }

    @Override
    public List<FinanceiroDrillNode> listarCtesDaFatura(int idFatura) {
        return List.of(
                new FinanceiroDrillNode("CTE377001", "CT-e 377001", "Cliente destino - 01/06/2026", new BigDecimal("120240.30"), 0, false),
                new FinanceiroDrillNode("CTE377002", "CT-e 377002", "Cliente destino - 02/06/2026", new BigDecimal("65000.00"), 0, false));
    }

    @Override
    public List<DreFaturaCte> listarCtesDetalhadosDaFatura(int idFatura) {
        return List.of(
                new DreFaturaCte(377001, "Sherwin-Williams do Brasil Ltda", "Loja Centro - SP", 120,
                        new BigDecimal("3450.00"), new BigDecimal("98200.00"), new BigDecimal("120240.30")),
                new DreFaturaCte(377002, "Universo Tintas e Vernizes Ltda", "Deposito Zona Sul - SP", 64,
                        new BigDecimal("1880.50"), new BigDecimal("52100.00"), new BigDecimal("65000.00")));
    }

    @Override
    public List<FinanceiroDrillNode> listarFornecedores(FinanceiroFiltro filtro) {
        return List.of(
                new FinanceiroDrillNode("702", "Fornecedor combustiveis", "1 duplicata(s)", new BigDecimal("67320.20"), 1, true),
                new FinanceiroDrillNode("820", "Oficina parceira", "1 duplicata(s)", new BigDecimal("31500.00"), 1, true));
    }

    @Override
    public List<FinanceiroDrillNode> listarNotasDoFornecedor(int idFornecedor, FinanceiroFiltro filtro) {
        return List.of(
                new FinanceiroDrillNode("NC88410", "NC-88410 (NF 12345)", "Entrada 01/06/2026", new BigDecimal("67320.20"), 2, true));
    }

    @Override
    public List<FinanceiroDrillNode> listarProdutosDaNota(int idNotaCompra) {
        return List.of(
                new FinanceiroDrillNode("PROD1", "Diesel S10", "Combustiveis / Frota", new BigDecimal("60000.00"), 0, false),
                new FinanceiroDrillNode("PROD2", "Arla 32", "Combustiveis / Frota", new BigDecimal("7320.20"), 0, false));
    }

    private FinanceiroMovimento movimento(FinanceiroNatureza natureza, FinanceiroStatus status, FinanceiroOrigemTipo tipo,
            Integer origemId, LocalDate competencia, LocalDate vencimento, LocalDate baixa, String valor, Integer bancoId,
            String banco, Integer pessoaId, String pessoa, Integer centroId, String centro, Integer filialId, String filial,
            Integer pcccId, String plano, String classificacao, String dmr, String documento, String historico,
            boolean tomadorExpressoSalome, boolean bancoPerdasDanos) {
        return new FinanceiroMovimento(natureza, status, tipo, origemId, competencia, vencimento, baixa,
                new BigDecimal(valor), bancoId, banco, pessoaId, pessoa, centroId, centro, filialId, filial, pcccId, plano,
                classificacao, dmr, documento, historico, tomadorExpressoSalome, bancoPerdasDanos, origin(tipo));
    }

    private br.com.salome.core.domain.legacy.LegacyOrigin origin(FinanceiroOrigemTipo tipo) {
        return switch (tipo) {
            case FATURA_BAIXA, FATURA_ABERTA -> FinanceiroRuleOrigins.FATURA;
            case CTE_ABERTO -> FinanceiroRuleOrigins.CTE_ABERTO;
            case CTE_EMITIDO -> FinanceiroRuleOrigins.CTE_EMITIDO;
            case PAGAMENTO_CAIXA, CAIXA_DINHEIRO -> FinanceiroRuleOrigins.PAGAMENTO_CAIXA;
            case EXTRATO_AVULSO -> FinanceiroRuleOrigins.EXTRATO_AVULSO;
            case NOTA_COMPRA_DUPLICATA -> FinanceiroRuleOrigins.NOTA_COMPRA_DUPLICATA;
            case NOTA_COMPRA_COMPETENCIA -> FinanceiroRuleOrigins.NOTA_COMPRA_COMPETENCIA;
        };
    }
}
