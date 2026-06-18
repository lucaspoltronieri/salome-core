package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.FinanceiroDashboardSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroGrupo;
import br.com.salome.core.domain.financeiro.FinanceiroKpi;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroResumoPrevistoRealizado;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.FinanceiroSeriePonto;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FinanceiroFluxoCaixaService {

    private final FinanceiroFluxoCaixaRepository repository;
    private final Clock clock;

    @Autowired
    public FinanceiroFluxoCaixaService(FinanceiroFluxoCaixaRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    FinanceiroFluxoCaixaService(FinanceiroFluxoCaixaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public FinanceiroDashboardSnapshot dashboard(FinanceiroFiltro filtro) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        List<FinanceiroMovimento> movimentosResumo = repository.listarMovimentos(normalizado).stream()
                .filter(movimento -> !movimento.receitaExcluida())
                .filter(movimento -> intersectaPeriodo(movimento, normalizado))
                .filter(movimento -> filtraNatureza(movimento, normalizado.natureza()))
                .filter(movimento -> filtraBusca(movimento, normalizado.busca()))
                .sorted(Comparator.comparing(FinanceiroMovimento::dataFluxo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FinanceiroMovimento::clienteFornecedor)
                        .thenComparing(FinanceiroMovimento::documento))
                .toList();

        List<FinanceiroMovimento> movimentosTabela = movimentosResumo.stream()
                .filter(movimento -> dentroPeriodoFluxo(movimento, normalizado))
                .filter(movimento -> filtraStatus(movimento, normalizado.status()))
                .filter(this::visivelNaTabela)
                .toList();

        BigDecimal receitasPrevistas = somaPrevisto(movimentosResumo, normalizado, FinanceiroNatureza.RECEITA);
        BigDecimal receitasRealizadas = somaRealizado(movimentosResumo, normalizado, FinanceiroNatureza.RECEITA);
        BigDecimal despesasPrevistas = somaPrevisto(movimentosResumo, normalizado, FinanceiroNatureza.DESPESA);
        BigDecimal despesasRealizadas = somaRealizado(movimentosResumo, normalizado, FinanceiroNatureza.DESPESA);
        BigDecimal saldoPrevisto = receitasPrevistas.subtract(despesasPrevistas);
        BigDecimal saldoRealizado = receitasRealizadas.subtract(despesasRealizadas);
        BigDecimal diferencaRecebimento = receitasRealizadas.subtract(receitasPrevistas);
        BigDecimal diferencaPagamento = despesasPrevistas.subtract(despesasRealizadas);
        BigDecimal diferencaSaldo = saldoRealizado.subtract(saldoPrevisto);
        List<FinanceiroSaldoBanco> saldosBancarios = repository.listarSaldosBancarios(normalizado);
        BigDecimal saldoBancario = saldosBancarios.stream()
                .map(FinanceiroSaldoBanco::saldoBancario)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceiroDashboardSnapshot(
                normalizado.inicio(),
                normalizado.fim(),
                Instant.now(clock),
                List.of(
                        new FinanceiroKpi("Saldo realizado", saldoRealizado, "Recebido menos pago no periodo", tom(saldoRealizado)),
                        new FinanceiroKpi("Saldo previsto", saldoPrevisto, "Previsto por vencimento no periodo", tom(saldoPrevisto)),
                        new FinanceiroKpi("Diferenca do periodo", diferencaSaldo, "Realizado menos previsto", tom(diferencaSaldo)),
                        new FinanceiroKpi("Saldo bancos/caixa", saldoBancario, "Posicao atual por banco, fora do periodo", tom(saldoBancario))),
                List.of(
                        new FinanceiroResumoPrevistoRealizado("Recebimentos", receitasPrevistas, receitasRealizadas,
                                diferencaRecebimento, "Realizado maior indica juros/outros recebimentos; menor indica aberto/abatimento.",
                                tom(diferencaRecebimento)),
                        new FinanceiroResumoPrevistoRealizado("Pagamentos", despesasPrevistas, despesasRealizadas,
                                diferencaPagamento, "Positivo indica contas previstas ainda nao pagas; negativo indica pagamento acima do previsto.",
                                tom(diferencaPagamento))),
                serie(normalizado, movimentosResumo),
                grupos(movimentosResumo, FinanceiroMovimento::dmr, 10),
                grupos(movimentosResumo.stream()
                        .filter(movimento -> movimento.natureza() == FinanceiroNatureza.DESPESA)
                        .toList(), FinanceiroMovimento::centroCusto, 10),
                grupos(movimentosResumo.stream().filter(this::temBancoInformado).toList(), FinanceiroMovimento::banco, 8),
                saldosBancarios,
                grupos(movimentosResumo, FinanceiroMovimento::clienteFornecedor, 12),
                movimentosTabela,
                alertas(movimentosResumo, movimentosTabela, repository.demonstrativo()),
                repository.demonstrativo());
    }

    public List<FinanceiroDrillNode> clientes(FinanceiroFiltro filtro) {
        return repository.listarClientes(normalizado(filtro));
    }

    public List<FinanceiroDrillNode> faturasDoCliente(int idCliente, FinanceiroFiltro filtro) {
        return repository.listarFaturasDoCliente(idCliente, normalizado(filtro));
    }

    public List<FinanceiroDrillNode> ctesDaFatura(int idFatura) {
        return repository.listarCtesDaFatura(idFatura);
    }

    public List<FinanceiroDrillNode> fornecedores(FinanceiroFiltro filtro) {
        return repository.listarFornecedores(normalizado(filtro));
    }

    public List<FinanceiroDrillNode> notasDoFornecedor(int idFornecedor, FinanceiroFiltro filtro) {
        return repository.listarNotasDoFornecedor(idFornecedor, normalizado(filtro));
    }

    public List<FinanceiroDrillNode> produtosDaNota(int idNotaCompra) {
        return repository.listarProdutosDaNota(idNotaCompra);
    }

    private FinanceiroFiltro normalizado(FinanceiroFiltro filtro) {
        return filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
    }

    private boolean dentroPeriodoFluxo(FinanceiroMovimento movimento, FinanceiroFiltro filtro) {
        LocalDate data = movimento.dataFluxo();
        return data != null && !data.isBefore(filtro.inicio()) && !data.isAfter(filtro.fim());
    }

    private boolean intersectaPeriodo(FinanceiroMovimento movimento, FinanceiroFiltro filtro) {
        return noPeriodo(movimento.dataCompetencia(), filtro)
                || noPeriodo(movimento.dataVencimento(), filtro)
                || noPeriodo(movimento.dataBaixa(), filtro)
                || dentroPeriodoFluxo(movimento, filtro);
    }

    private boolean filtraStatus(FinanceiroMovimento movimento, String status) {
        return "TODOS".equalsIgnoreCase(status) || movimento.status().name().equalsIgnoreCase(status);
    }

    private boolean filtraNatureza(FinanceiroMovimento movimento, String natureza) {
        return "TODAS".equalsIgnoreCase(natureza) || movimento.natureza().name().equalsIgnoreCase(natureza);
    }

    private boolean filtraBusca(FinanceiroMovimento movimento, String busca) {
        if (busca == null || busca.isBlank()) {
            return true;
        }
        String needle = busca.toLowerCase(Locale.ROOT);
        return List.of(movimento.clienteFornecedor(), movimento.banco(), movimento.centroCusto(), movimento.planoContas(),
                        movimento.dmr(), movimento.documento(), movimento.historico())
                .stream()
                .filter(Objects::nonNull)
                .map(valor -> valor.toLowerCase(Locale.ROOT))
                .anyMatch(valor -> valor.contains(needle));
    }

    private boolean visivelNaTabela(FinanceiroMovimento movimento) {
        return temBancoInformado(movimento) || movimento.origemTipo().name().equals("CTE_ABERTO");
    }

    private boolean temBancoInformado(FinanceiroMovimento movimento) {
        return movimento.banco() != null && !"Nao informado".equals(movimento.banco());
    }

    private BigDecimal soma(List<FinanceiroMovimento> movimentos, FinanceiroNatureza natureza, FinanceiroStatus status) {
        return movimentos.stream()
                .filter(movimento -> movimento.natureza() == natureza && movimento.status() == status)
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somaPrevisto(List<FinanceiroMovimento> movimentos, FinanceiroFiltro filtro,
            FinanceiroNatureza natureza) {
        return movimentos.stream()
                .filter(movimento -> movimento.natureza() == natureza)
                .filter(movimento -> noPeriodo(movimento.dataVencimento(), filtro))
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somaRealizado(List<FinanceiroMovimento> movimentos, FinanceiroFiltro filtro,
            FinanceiroNatureza natureza) {
        return movimentos.stream()
                .filter(movimento -> movimento.natureza() == natureza && movimento.status() == FinanceiroStatus.REALIZADO)
                .filter(movimento -> noPeriodo(movimento.dataBaixa(), filtro))
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean noPeriodo(LocalDate data, FinanceiroFiltro filtro) {
        return data != null && !data.isBefore(filtro.inicio()) && !data.isAfter(filtro.fim());
    }

    private List<FinanceiroSeriePonto> serie(FinanceiroFiltro filtro, List<FinanceiroMovimento> movimentos) {
        List<FinanceiroSeriePonto> pontos = new ArrayList<>();
        for (LocalDate data = filtro.inicio(); !data.isAfter(filtro.fim()); data = data.plusDays(1)) {
            BigDecimal receitasPrevistas = somaPorVencimento(movimentos, data, FinanceiroNatureza.RECEITA);
            BigDecimal receitasRealizadas = somaPorBaixa(movimentos, data, FinanceiroNatureza.RECEITA);
            BigDecimal despesasPrevistas = somaPorVencimento(movimentos, data, FinanceiroNatureza.DESPESA);
            BigDecimal despesasRealizadas = somaPorBaixa(movimentos, data, FinanceiroNatureza.DESPESA);
            pontos.add(new FinanceiroSeriePonto(data, receitasPrevistas, receitasRealizadas, despesasPrevistas,
                    despesasRealizadas, receitasPrevistas.subtract(despesasPrevistas),
                    receitasRealizadas.subtract(despesasRealizadas)));
        }
        return pontos;
    }

    private BigDecimal somaPorVencimento(List<FinanceiroMovimento> movimentos, LocalDate data,
            FinanceiroNatureza natureza) {
        return movimentos.stream()
                .filter(movimento -> movimento.natureza() == natureza)
                .filter(movimento -> data.equals(movimento.dataVencimento()))
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somaPorBaixa(List<FinanceiroMovimento> movimentos, LocalDate data,
            FinanceiroNatureza natureza) {
        return movimentos.stream()
                .filter(movimento -> movimento.natureza() == natureza && movimento.status() == FinanceiroStatus.REALIZADO)
                .filter(movimento -> data.equals(movimento.dataBaixa()))
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<FinanceiroGrupo> grupos(List<FinanceiroMovimento> movimentos, Function<FinanceiroMovimento, String> chave,
            int limite) {
        return movimentos.stream()
                .collect(Collectors.groupingBy(chave, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> grupo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FinanceiroGrupo::saldo, Comparator.comparing(BigDecimal::abs)).reversed())
                .limit(limite)
                .toList();
    }

    private FinanceiroGrupo grupo(String chave, List<FinanceiroMovimento> movimentos) {
        BigDecimal receitas = movimentos.stream()
                .filter(movimento -> movimento.natureza() == FinanceiroNatureza.RECEITA)
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal despesas = movimentos.stream()
                .filter(movimento -> movimento.natureza() == FinanceiroNatureza.DESPESA)
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceiroGrupo(chave, receitas, despesas, receitas.subtract(despesas), movimentos.size());
    }

    private List<String> alertas(List<FinanceiroMovimento> movimentosResumo, List<FinanceiroMovimento> movimentosTabela,
            boolean demonstrativo) {
        List<String> alertas = new ArrayList<>();
        if (demonstrativo) {
            alertas.add("Modo demonstrativo ativo: os 4 movimentos visiveis sao amostras. Ligue o datasource legado para ver todos os lancamentos reais.");
        }
        if (movimentosTabela.size() < movimentosResumo.size()) {
            alertas.add("O filtro de status afeta a tabela; KPIs e graficos comparam previsto x realizado do periodo completo.");
        }
        long semDmr = movimentosResumo.stream().filter(movimento -> "Nao informado".equals(movimento.dmr())).count();
        long extratoAvulso = movimentosResumo.stream().filter(movimento -> movimento.origemTipo().name().equals("EXTRATO_AVULSO")).count();
        if (semDmr > 0) {
            alertas.add(semDmr + " movimentos sem DMR/plano classificado.");
        }
        if (extratoAvulso > 0) {
            alertas.add(extratoAvulso + " lancamentos vieram direto do extrato bancario.");
        }
        alertas.add("Receitas do tomador Expresso Salome e do banco 34 foram removidas do painel.");
        return alertas;
    }

    private String tom(BigDecimal valor) {
        return valor.signum() >= 0 ? "positivo" : "negativo";
    }
}
