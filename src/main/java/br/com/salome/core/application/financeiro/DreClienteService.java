package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.DreClienteDetalhe;
import br.com.salome.core.domain.financeiro.DreClienteDriver;
import br.com.salome.core.domain.financeiro.DreClienteLinha;
import br.com.salome.core.domain.financeiro.DreClienteSnapshot;
import br.com.salome.core.domain.financeiro.DreContaNo;
import br.com.salome.core.domain.financeiro.DreLinha;
import br.com.salome.core.domain.financeiro.DreOrigemResumo;
import br.com.salome.core.domain.financeiro.DreResumoLiquidez;
import br.com.salome.core.domain.financeiro.DreSecao;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DRE por cliente em regime de caixa: receita realizada direta por cliente (a baixa de fatura ja
 * carrega o cliente) menos o rateio do bolo de despesas (mesmos centros de custo do DRE caixa) pelo
 * criterio escolhido - Peso (tonelada), Valor de frete ou Numero de CT-es. Por construcao os pesos
 * somam 1, entao a soma dos resultados por cliente fecha com o resultado liquido do DRE caixa.
 *
 * <p>A classificacao por secao e a montagem da arvore sintetico/analitico sao copiadas de proposito
 * do {@link DreGerencialService} (mesmo criterio adotado no DRE competencia) para nao tocar no DRE
 * caixa, que esta validado e travado.
 */
@Service
public class DreClienteService {

    static final String RECEITA = "RECEITA";
    static final String DEDUCOES = "DEDUCOES";
    static final String CUSTOS_SERVICOS = "CUSTOS_SERVICOS";
    static final String DESPESAS_COMERCIAIS = "DESPESAS_COMERCIAIS";
    static final String DESPESAS_ADMINISTRATIVAS = "DESPESAS_ADMINISTRATIVAS";
    static final String DEPRECIACAO_AMORTIZACAO = "DEPRECIACAO_AMORTIZACAO";
    static final String RESULTADO_FINANCEIRO = "RESULTADO_FINANCEIRO";
    static final String IMPOSTOS = "IMPOSTOS";
    static final String TRANSFERENCIA = "TRANSFERENCIA";

    static final String DRIVER_PESO = "PESO";
    static final String DRIVER_FRETE = "FRETE";
    static final String DRIVER_CTES = "CTES";

    static final String REGIME_CAIXA = "CAIXA";
    static final String REGIME_COMPETENCIA = "COMPETENCIA";

    static final String CONTA_SEM_CC_KEY = "~SEM_CC";
    static final String CONTA_BANCARIAS_EXTRATO_KEY = "~BANCARIAS_EXTRATO";
    private static final String CONTA_SEM_CC = "Sem centro de custo";
    private static final String CONTA_BANCARIAS_EXTRATO = "Despesas Bancarias Extrato";

    private static final int CLIENTE_SEM_ID = 0;
    private static final String CLIENTE_SEM_NOME = "Sem cliente";

    private static final List<String> ORDEM_SECOES = List.of(RECEITA, DEDUCOES, CUSTOS_SERVICOS,
            DESPESAS_COMERCIAIS, DESPESAS_ADMINISTRATIVAS, DEPRECIACAO_AMORTIZACAO, RESULTADO_FINANCEIRO,
            IMPOSTOS, TRANSFERENCIA);

    private static final Map<String, String> TITULOS_SECAO = Map.ofEntries(
            Map.entry(RECEITA, "Receita operacional recebida"),
            Map.entry(DEDUCOES, "Deducoes da receita"),
            Map.entry(CUSTOS_SERVICOS, "Custos operacionais (frota e carga)"),
            Map.entry(DESPESAS_COMERCIAIS, "Despesas comerciais"),
            Map.entry(DESPESAS_ADMINISTRATIVAS, "Despesas administrativas e gerais"),
            Map.entry(DEPRECIACAO_AMORTIZACAO, "Investimentos e imobilizado"),
            Map.entry(RESULTADO_FINANCEIRO, "Despesas financeiras"),
            Map.entry(IMPOSTOS, "Impostos"),
            Map.entry(TRANSFERENCIA, "Transferencias"));

    private final FinanceiroFluxoCaixaRepository repository;
    private final Clock clock;

    @Autowired
    public DreClienteService(FinanceiroFluxoCaixaRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    DreClienteService(FinanceiroFluxoCaixaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------
    // Ranking de clientes
    // ------------------------------------------------------------------------------------------------

    public DreClienteSnapshot dashboard(FinanceiroFiltro filtro, Integer filialId, String filial, String driver,
            String regime) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        String regimeEfetivo = normalizarRegime(regime);
        String driverPedido = normalizarDriver(driver);
        Map<String, PlanoConta> plano = planoMap();
        List<FinanceiroMovimento> movimentos = movimentosDoRegime(normalizado, filialId, filial, regimeEfetivo);
        Rateio rateio = calcularPesos(normalizado, movimentos, plano, driverPedido);

        String busca = normaliza(normalizado.busca());
        List<DreClienteLinha> linhas = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entrada : rateio.receitaPorCliente().entrySet()) {
            int id = entrada.getKey();
            String nome = rateio.nomePorCliente().getOrDefault(id, CLIENTE_SEM_NOME);
            if (!busca.isBlank() && !normaliza(nome).contains(busca)) {
                continue;
            }
            BigDecimal receita = entrada.getValue();
            BigDecimal despesa = rateio.despesaPorCliente().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal resultado = receita.subtract(despesa);
            DreClienteDriver d = rateio.driverPorCliente().get(id);
            BigDecimal toneladas = d == null ? BigDecimal.ZERO : d.toneladas();
            int qtdCtes = d == null ? 0 : d.qtdCtes();
            linhas.add(new DreClienteLinha(id, nome, receita, pct(rateio.pesoPorCliente().getOrDefault(id, BigDecimal.ZERO)),
                    toneladas, qtdCtes, despesa, resultado, percentual(resultado, receita)));
        }
        linhas.sort(Comparator.comparing(DreClienteLinha::resultado, Comparator.reverseOrder()));

        BigDecimal resultadoTotal = rateio.receitaTotal().subtract(rateio.despesaTotal());

        return new DreClienteSnapshot(normalizado.inicio(), normalizado.fim(), Instant.now(clock),
                regimeEfetivo, rateio.driverEfetivo(), rateio.receitaTotal(), rateio.despesaTotal(), resultadoTotal,
                percentual(resultadoTotal, rateio.receitaTotal()), linhas.size(), rateio.toneladasTotal(),
                rateio.qtdCtesTotal(), rateio.custoPorTonelada(), rateio.toneladasNaoAtribuidas(),
                rateio.despesaNaoAtribuida(), linhas, alertasDashboard(rateio), repository.demonstrativo());
    }

    // ------------------------------------------------------------------------------------------------
    // DRE detalhado de um cliente (secoes escaladas)
    // ------------------------------------------------------------------------------------------------

    public DreClienteDetalhe dreDoCliente(int idCliente, FinanceiroFiltro filtro, Integer filialId, String filial,
            String driver, String regime) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        String regimeEfetivo = normalizarRegime(regime);
        String driverPedido = normalizarDriver(driver);
        Map<String, PlanoConta> plano = planoMap();
        List<FinanceiroMovimento> movimentos = movimentosDoRegime(normalizado, filialId, filial, regimeEfetivo);
        Rateio rateio = calcularPesos(normalizado, movimentos, plano, driverPedido);

        BigDecimal fator = rateio.pesoPorCliente().getOrDefault(idCliente, BigDecimal.ZERO);
        String nome = rateio.nomePorCliente().getOrDefault(idCliente, "Cliente " + idCliente);

        List<FinanceiroMovimento> receitaMovs = movimentos.stream()
                .filter(m -> m.natureza() == FinanceiroNatureza.RECEITA)
                .filter(m -> clienteKey(m) == idCliente)
                .toList();
        List<FinanceiroMovimento> despesaMovs = movimentos.stream()
                .filter(m -> m.natureza() == FinanceiroNatureza.DESPESA)
                .toList();

        Map<String, BigDecimal> somaReceita = somasPorSecao(receitaMovs, plano);
        Map<String, BigDecimal> somaDespesaGlobal = somasPorSecao(despesaMovs, plano);
        BigDecimal receita = somaReceita.getOrDefault(RECEITA, BigDecimal.ZERO);
        // Deducoes = parte de receita (3.02) + parte de despesa (grupo "deducoes de vendas") rateada,
        // igual ao DRE caixa, que joga as deducoes na receita liquida.
        BigDecimal deducoes = somaReceita.getOrDefault(DEDUCOES, BigDecimal.ZERO)
                .add(escalar(somaDespesaGlobal.get(DEDUCOES), fator));
        BigDecimal receitaLiquida = receita.add(deducoes);

        BigDecimal custos = escalar(somaDespesaGlobal.get(CUSTOS_SERVICOS), fator);
        BigDecimal comerciais = escalar(somaDespesaGlobal.get(DESPESAS_COMERCIAIS), fator);
        BigDecimal administrativas = escalar(somaDespesaGlobal.get(DESPESAS_ADMINISTRATIVAS), fator);
        BigDecimal depreciacao = escalar(somaDespesaGlobal.get(DEPRECIACAO_AMORTIZACAO), fator);
        BigDecimal financeiro = escalar(somaDespesaGlobal.get(RESULTADO_FINANCEIRO), fator);
        BigDecimal impostos = escalar(somaDespesaGlobal.get(IMPOSTOS), fator);

        BigDecimal margemBruta = receitaLiquida.add(custos);
        BigDecimal ebitda = margemBruta.add(comerciais).add(administrativas);
        BigDecimal ebit = ebitda.add(depreciacao);
        BigDecimal resultadoLiquido = ebit.add(financeiro).add(impostos);

        Map<String, DreSecao> porCodigo = new LinkedHashMap<>();
        for (DreSecao secao : construirSecoes(receitaMovs, plano, receitaLiquida)) {
            porCodigo.put(secao.codigo(), secao);
        }
        for (DreSecao secao : construirSecoes(despesaMovs, plano, receitaLiquida)) {
            porCodigo.put(secao.codigo(), escalarSecao(secao, fator, receitaLiquida));
        }
        List<DreSecao> secoes = new ArrayList<>();
        for (String codigo : ORDEM_SECOES) {
            DreSecao secao = porCodigo.get(codigo);
            if (secao != null) {
                secoes.add(secao);
            }
        }

        List<DreLinha> linhas = List.of(
                calculada("RECEITA_LIQUIDA", "Receita liquida caixa", receitaLiquida, receitaLiquida),
                calculada("MARGEM_BRUTA", "Margem bruta", margemBruta, receitaLiquida),
                calculada("EBITDA", "EBITDA caixa gerencial", ebitda, receitaLiquida),
                calculada("EBIT", "EBIT caixa gerencial", ebit, receitaLiquida),
                calculada("RESULTADO_LIQUIDO", "Resultado liquido gerencial de caixa", resultadoLiquido, receitaLiquida));

        BigDecimal despesaApropriada = rateio.despesaPorCliente().getOrDefault(idCliente, BigDecimal.ZERO);
        BigDecimal resultado = receita.subtract(despesaApropriada);
        DreClienteDriver d = rateio.driverPorCliente().get(idCliente);

        List<String> alertas = new ArrayList<>();
        alertas.add("Despesas rateadas pelo criterio " + rotuloDriver(rateio.driverEfetivo())
                + " (" + pct(fator).setScale(2, RoundingMode.HALF_UP) + "% do bolo de despesas).");
        alertas.addAll(alertasDashboard(rateio));

        return new DreClienteDetalhe(idCliente, nome, regimeEfetivo, rateio.driverEfetivo(), normalizado.inicio(),
                normalizado.fim(), Instant.now(clock), receita, despesaApropriada, resultado, percentual(resultado, receita),
                pct(fator), d == null ? BigDecimal.ZERO : d.toneladas(), d == null ? 0 : d.qtdCtes(),
                resumos(receitaLiquida, margemBruta, ebitda, resultadoLiquido), linhas, secoes, alertas,
                repository.demonstrativo());
    }

    // ------------------------------------------------------------------------------------------------
    // Rateio
    // ------------------------------------------------------------------------------------------------

    private Rateio calcularPesos(FinanceiroFiltro normalizado, List<FinanceiroMovimento> movimentos,
            Map<String, PlanoConta> plano, String driverPedido) {
        Map<Integer, BigDecimal> receitaPorCliente = new LinkedHashMap<>();
        Map<Integer, String> nomePorCliente = new LinkedHashMap<>();
        for (FinanceiroMovimento m : movimentos) {
            if (m.natureza() != FinanceiroNatureza.RECEITA) {
                continue;
            }
            int id = clienteKey(m);
            receitaPorCliente.merge(id, m.valor(), BigDecimal::add);
            nomePorCliente.putIfAbsent(id, id == CLIENTE_SEM_ID ? CLIENTE_SEM_NOME : m.clienteFornecedor());
        }
        BigDecimal receitaTotal = receitaPorCliente.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Bolo de despesa = despesa que entra no Resultado liquido do DRE caixa: todas as secoes de
        // despesa menos TRANSFERENCIA (transferencia entre contas nao e P&L e fica fora do resultado).
        List<FinanceiroMovimento> despesaMovs = movimentos.stream()
                .filter(m -> m.natureza() == FinanceiroNatureza.DESPESA)
                .toList();
        Map<String, BigDecimal> despesaSecao = somasPorSecao(despesaMovs, plano);
        BigDecimal chain = despesaSecao.entrySet().stream()
                .filter(e -> !TRANSFERENCIA.equals(e.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal despesaTotal = chain.negate();
        BigDecimal transferenciaDespesa = despesaSecao.getOrDefault(TRANSFERENCIA, BigDecimal.ZERO).negate();

        // Total por EMISSAO derivado dos drivers (todos os tomadores, inclui Expresso e sem tomador):
        // igual ao somarToneladasTransportadas do DRE gerencial. O rateio usa esse total como base,
        // entao o custo/tonelada bate com o gerencial; a parte de clientes sem receita no periodo fica
        // como "nao atribuido".
        List<DreClienteDriver> driverRows = repository.listarDriversRateioPorCliente(normalizado);
        BigDecimal toneladasTotal = driverRows.stream().map(DreClienteDriver::toneladas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int qtdCtesTotal = driverRows.stream().mapToInt(DreClienteDriver::qtdCtes).sum();
        Map<Integer, DreClienteDriver> driverPorCliente = driverRows.stream()
                .filter(d -> d.idCliente() != null)
                .collect(Collectors.toMap(DreClienteDriver::idCliente, d -> d, (a, b) -> a, LinkedHashMap::new));

        List<String> alertas = new ArrayList<>();
        String driverEfetivo = driverPedido;
        LinkedHashMap<Integer, BigDecimal> numeradores = numeradores(receitaPorCliente, driverPorCliente, driverEfetivo);
        BigDecimal denom = denominadorTotal(driverEfetivo, toneladasTotal, qtdCtesTotal, receitaTotal);
        BigDecimal somaNum = numeradores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if ((denom.signum() == 0 || somaNum.signum() == 0) && !DRIVER_FRETE.equals(driverEfetivo)) {
            alertas.add("Sem " + (DRIVER_PESO.equals(driverPedido) ? "tonelada" : "CT-e")
                    + " no periodo para o rateio; usando Valor de frete.");
            driverEfetivo = DRIVER_FRETE;
            numeradores = numeradores(receitaPorCliente, driverPorCliente, driverEfetivo);
            denom = receitaTotal;
            somaNum = numeradores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<Integer, BigDecimal> pesoPorCliente = new LinkedHashMap<>();
        for (Map.Entry<Integer, BigDecimal> e : numeradores.entrySet()) {
            BigDecimal fracao = denom.signum() == 0 ? BigDecimal.ZERO
                    : e.getValue().divide(denom, 10, RoundingMode.HALF_UP);
            pesoPorCliente.put(e.getKey(), fracao);
        }

        // Custo atribuido aos clientes = bolo x (soma dos numeradores deles / total). O restante e o
        // "nao atribuido". Distribui o atribuido entre os clientes pelos numeradores (residuo no maior).
        BigDecimal despesaAtribuida = denom.signum() == 0 ? BigDecimal.ZERO
                : despesaTotal.multiply(somaNum).divide(denom, 2, RoundingMode.HALF_UP);
        Map<Integer, BigDecimal> despesaPorCliente = ratear(despesaAtribuida, numeradores, somaNum);
        BigDecimal despesaNaoAtribuida = despesaTotal.subtract(despesaAtribuida);

        BigDecimal toneladasAtribuidas = receitaPorCliente.keySet().stream()
                .map(driverPorCliente::get)
                .filter(Objects::nonNull)
                .map(DreClienteDriver::toneladas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toneladasNaoAtribuidas = toneladasTotal.subtract(toneladasAtribuidas).max(BigDecimal.ZERO);
        BigDecimal custoPorTonelada = toneladasTotal.signum() == 0 ? BigDecimal.ZERO
                : despesaTotal.divide(toneladasTotal, 2, RoundingMode.HALF_UP);

        return new Rateio(driverEfetivo, receitaPorCliente, nomePorCliente, driverPorCliente, receitaTotal,
                despesaTotal, transferenciaDespesa, pesoPorCliente, despesaPorCliente, toneladasTotal, qtdCtesTotal,
                custoPorTonelada, toneladasNaoAtribuidas, despesaNaoAtribuida, alertas);
    }

    private BigDecimal denominadorTotal(String driver, BigDecimal toneladasTotal, int qtdCtesTotal,
            BigDecimal receitaTotal) {
        return switch (driver) {
            case DRIVER_FRETE -> receitaTotal;
            case DRIVER_CTES -> BigDecimal.valueOf(qtdCtesTotal);
            default -> toneladasTotal;
        };
    }

    private LinkedHashMap<Integer, BigDecimal> numeradores(Map<Integer, BigDecimal> receitaPorCliente,
            Map<Integer, DreClienteDriver> driverPorCliente, String driver) {
        LinkedHashMap<Integer, BigDecimal> numeradores = new LinkedHashMap<>();
        for (Integer id : receitaPorCliente.keySet()) {
            BigDecimal num;
            if (DRIVER_FRETE.equals(driver)) {
                num = receitaPorCliente.get(id);
            } else if (DRIVER_CTES.equals(driver)) {
                DreClienteDriver d = driverPorCliente.get(id);
                num = d == null ? BigDecimal.ZERO : BigDecimal.valueOf(d.qtdCtes());
            } else {
                DreClienteDriver d = driverPorCliente.get(id);
                num = d == null ? BigDecimal.ZERO : d.toneladas();
            }
            numeradores.put(id, num == null ? BigDecimal.ZERO : num);
        }
        return numeradores;
    }

    /** Rateia {@code total} pelos numeradores, jogando o residuo do arredondamento no maior cliente. */
    private Map<Integer, BigDecimal> ratear(BigDecimal total, LinkedHashMap<Integer, BigDecimal> numeradores,
            BigDecimal denom) {
        Map<Integer, BigDecimal> resultado = new LinkedHashMap<>();
        if (denom.signum() == 0) {
            numeradores.keySet().forEach(id -> resultado.put(id, BigDecimal.ZERO));
            return resultado;
        }
        BigDecimal acumulado = BigDecimal.ZERO;
        Integer maiorId = null;
        BigDecimal maiorNum = null;
        for (Map.Entry<Integer, BigDecimal> e : numeradores.entrySet()) {
            BigDecimal parte = total.multiply(e.getValue()).divide(denom, 2, RoundingMode.HALF_UP);
            resultado.put(e.getKey(), parte);
            acumulado = acumulado.add(parte);
            if (maiorNum == null || e.getValue().compareTo(maiorNum) > 0) {
                maiorNum = e.getValue();
                maiorId = e.getKey();
            }
        }
        BigDecimal residuo = total.subtract(acumulado);
        if (residuo.signum() != 0 && maiorId != null) {
            resultado.merge(maiorId, residuo, BigDecimal::add);
        }
        return resultado;
    }

    private List<String> alertasDashboard(Rateio rateio) {
        List<String> alertas = new ArrayList<>(rateio.alertas());
        if (rateio.despesaNaoAtribuida().compareTo(new BigDecimal("0.01")) >= 0) {
            alertas.add("Nao atribuido: " + rateio.despesaNaoAtribuida().setScale(2, RoundingMode.HALF_UP)
                    + " em " + rateio.toneladasNaoAtribuidas().setScale(2, RoundingMode.HALF_UP)
                    + " t (carga de clientes sem receita no periodo: Expresso, sem tomador ou sem fatura no periodo). "
                    + "Os clientes listados somam o restante do bolo.");
        }
        if (rateio.transferenciaDespesa().signum() != 0) {
            alertas.add("Transferencias de " + rateio.transferenciaDespesa().setScale(2, RoundingMode.HALF_UP)
                    + " ficaram fora do rateio (nao entram no resultado, igual ao DRE caixa).");
        }
        if (DRIVER_FRETE.equals(rateio.driverEfetivo())) {
            alertas.add("Criterio Valor de frete: a margem % fica igual para todos os clientes (so muda o R$). "
                    + "Use o criterio Peso para revelar clientes sub-precificados.");
        } else {
            long semDriver = rateio.receitaPorCliente().entrySet().stream()
                    .filter(e -> e.getValue().signum() != 0)
                    .filter(e -> rateio.pesoPorCliente().getOrDefault(e.getKey(), BigDecimal.ZERO).signum() == 0)
                    .count();
            if (semDriver > 0) {
                alertas.add(semDriver + " cliente(s) com receita e sem "
                        + (DRIVER_PESO.equals(rateio.driverEfetivo()) ? "tonelada" : "CT-e")
                        + " no periodo ficaram com custo zero (margem 100%).");
            }
        }
        if (repository.demonstrativo()) {
            alertas.add("Modo demonstrativo ativo: conecte o datasource legado para ver dados reais.");
        }
        return alertas;
    }

    // ------------------------------------------------------------------------------------------------
    // Filtro por regime (caixa espelha o DRE caixa; competencia espelha o DRE competencia)
    // ------------------------------------------------------------------------------------------------

    private List<FinanceiroMovimento> movimentosDoRegime(FinanceiroFiltro normalizado, Integer filialId, String filial,
            String regime) {
        if (REGIME_COMPETENCIA.equals(regime)) {
            return repository.listarMovimentosCompetencia(normalizado).stream()
                    .filter(m -> dentroPeriodoCompetencia(m, normalizado))
                    .filter(m -> filialId == null || Objects.equals(m.filialId(), filialId))
                    .filter(m -> filtraFilial(m, filial))
                    .filter(m -> !m.receitaExcluida())
                    .filter(this::despesaPermitidaCompetencia)
                    .toList();
        }
        return repository.listarMovimentos(normalizado).stream()
                .filter(m -> m.status() == FinanceiroStatus.REALIZADO)
                .filter(m -> dentroPeriodoBaixa(m, normalizado))
                .filter(m -> filialId == null || Objects.equals(m.filialId(), filialId))
                .filter(m -> filtraFilial(m, filial))
                .filter(m -> !m.receitaExcluida())
                .toList();
    }

    private boolean dentroPeriodoBaixa(FinanceiroMovimento movimento, FinanceiroFiltro filtro) {
        return movimento.dataBaixa() != null
                && !movimento.dataBaixa().isBefore(filtro.inicio())
                && !movimento.dataBaixa().isAfter(filtro.fim());
    }

    private boolean dentroPeriodoCompetencia(FinanceiroMovimento movimento, FinanceiroFiltro filtro) {
        return movimento.dataCompetencia() != null
                && !movimento.dataCompetencia().isBefore(filtro.inicio())
                && !movimento.dataCompetencia().isAfter(filtro.fim());
    }

    /** Mesma regra do DRE competencia: despesa com plano so entra com dmr=SIM e classificacao nao "1". */
    private boolean despesaPermitidaCompetencia(FinanceiroMovimento movimento) {
        if (movimento.natureza() != FinanceiroNatureza.DESPESA) {
            return true;
        }
        String classificacao = classificacaoOuNula(movimento);
        if (classificacao == null) {
            return true;
        }
        if (classificacao.startsWith("1")) {
            return false;
        }
        return "sim".equals(normaliza(movimento.dmr()));
    }

    private boolean filtraFilial(FinanceiroMovimento movimento, String filial) {
        if (filial == null || filial.isBlank()) {
            return true;
        }
        String alvo = normaliza(filial);
        String valor = normaliza(movimento.filial());
        if (valor.equals(alvo) || valor.contains(alvo) || alvo.contains(valor)) {
            return true;
        }
        return switch (alvo) {
            case "spo" -> valor.contains("osasco") || valor.contains("sao paulo");
            case "cam" -> valor.contains("campinas");
            case "rib" -> valor.contains("ribeirao");
            case "sjp" -> valor.contains("sao jose") || valor.contains("rio preto");
            default -> false;
        };
    }

    private int clienteKey(FinanceiroMovimento movimento) {
        return movimento.clienteFornecedorId() == null ? CLIENTE_SEM_ID : movimento.clienteFornecedorId();
    }

    private Map<String, PlanoConta> planoMap() {
        return repository.listarPlanoContas().stream()
                .collect(Collectors.toMap(PlanoConta::classificacao, p -> p, (a, b) -> a, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------------------------------------
    // Secoes (copiado de proposito do DRE caixa)
    // ------------------------------------------------------------------------------------------------

    private Map<String, BigDecimal> somasPorSecao(List<FinanceiroMovimento> movimentos, Map<String, PlanoConta> plano) {
        Map<String, BigDecimal> somas = new LinkedHashMap<>();
        for (FinanceiroMovimento m : movimentos) {
            somas.merge(classificar(m, plano).secao(), valorAssinado(m), BigDecimal::add);
        }
        return somas;
    }

    private List<DreSecao> construirSecoes(List<FinanceiroMovimento> movimentos, Map<String, PlanoConta> plano,
            BigDecimal receitaLiquida) {
        List<MovAtrib> classificados = movimentos.stream()
                .map(m -> new MovAtrib(m, classificar(m, plano)))
                .toList();
        Map<String, List<MovAtrib>> porSecao = classificados.stream()
                .collect(Collectors.groupingBy(item -> item.atrib().secao(), LinkedHashMap::new, Collectors.toList()));
        List<DreSecao> secoes = new ArrayList<>();
        for (String codigo : ORDEM_SECOES) {
            List<MovAtrib> itens = porSecao.get(codigo);
            if (itens == null || itens.isEmpty()) {
                continue;
            }
            BigDecimal valor = secaoValor(itens);
            List<DreContaNo> contas = construirArvore(itens, plano, receitaLiquida);
            secoes.add(new DreSecao(codigo, TITULOS_SECAO.getOrDefault(codigo, codigo), valor,
                    percentual(valor, receitaLiquida), itens.size(), !RECEITA.equals(codigo), contas));
        }
        return secoes;
    }

    private Atribuicao classificar(FinanceiroMovimento movimento, Map<String, PlanoConta> plano) {
        String classificacao = classificacaoOuNula(movimento);
        if (movimento.natureza() == FinanceiroNatureza.RECEITA) {
            if (classificacao != null && classificacao.startsWith("3.02")) {
                return new Atribuicao(DEDUCOES, classificacao, descricaoConta(movimento, classificacao, plano));
            }
            String chave = classificacao == null ? "~RECEITA" : classificacao;
            return new Atribuicao(RECEITA, chave, descricaoConta(movimento, classificacao, plano));
        }
        if (classificacao == null) {
            if (movimento.origemTipo() == FinanceiroOrigemTipo.EXTRATO_AVULSO) {
                return new Atribuicao(RESULTADO_FINANCEIRO, CONTA_BANCARIAS_EXTRATO_KEY, CONTA_BANCARIAS_EXTRATO);
            }
            return new Atribuicao(DESPESAS_ADMINISTRATIVAS, CONTA_SEM_CC_KEY, CONTA_SEM_CC);
        }
        String secao = secaoPorDescricao(classificacao, movimento, plano);
        PlanoConta conta = plano.get(classificacao);
        if (DESPESAS_ADMINISTRATIVAS.equals(secao) && conta != null && conta.impostosFinanceiro()) {
            secao = RESULTADO_FINANCEIRO;
        }
        return new Atribuicao(secao, classificacao, descricaoConta(movimento, classificacao, plano));
    }

    private String secaoPorDescricao(String classificacao, FinanceiroMovimento movimento, Map<String, PlanoConta> plano) {
        PlanoConta grupo = plano.get(grupoSintetico(classificacao));
        String base = grupo != null ? grupo.descricao() : movimento.planoContas();
        String texto = normaliza(base == null ? "" : base);
        if (texto.contains("dedu")) {
            return DEDUCOES;
        }
        if (texto.contains("custo")) {
            return CUSTOS_SERVICOS;
        }
        if (texto.contains("financ") || texto.contains("parcelament") || texto.contains("juro")
                || texto.contains("bancar")) {
            return RESULTADO_FINANCEIRO;
        }
        if (texto.contains("imposto") || texto.contains("tribut") || texto.contains("fiscal")) {
            return IMPOSTOS;
        }
        if (texto.contains("propaganda") || texto.contains("comercial") || texto.contains("marketing")
                || texto.contains("publicidade")) {
            return DESPESAS_COMERCIAIS;
        }
        if (texto.contains("invest") || texto.contains("imobiliz") || texto.contains("deprecia")
                || texto.contains("amortiza")) {
            return DEPRECIACAO_AMORTIZACAO;
        }
        return DESPESAS_ADMINISTRATIVAS;
    }

    private String grupoSintetico(String classificacao) {
        String[] segmentos = classificacao.split("\\.");
        if (segmentos.length <= 1) {
            return classificacao;
        }
        return segmentos[0] + "." + segmentos[1];
    }

    private String classificacaoOuNula(FinanceiroMovimento movimento) {
        String classificacao = movimento.classificacao();
        if (classificacao == null || classificacao.isBlank() || classificacao.equalsIgnoreCase("Nao informado")) {
            return null;
        }
        return classificacao.trim();
    }

    private String descricaoConta(FinanceiroMovimento movimento, String classificacao, Map<String, PlanoConta> plano) {
        if (classificacao != null && plano.containsKey(classificacao)) {
            return plano.get(classificacao).descricao();
        }
        String descricao = movimento.planoContas();
        if (descricao == null || descricao.isBlank() || descricao.equalsIgnoreCase("Nao informado")) {
            return classificacao == null ? "Sem classificacao" : classificacao;
        }
        return descricao;
    }

    private List<DreContaNo> construirArvore(List<MovAtrib> itens, Map<String, PlanoConta> plano,
            BigDecimal receitaLiquida) {
        Map<String, BigDecimal> valor = new LinkedHashMap<>();
        Map<String, Integer> quantidade = new LinkedHashMap<>();
        Map<String, String> descricaoFolha = new LinkedHashMap<>();
        Map<String, List<FinanceiroMovimento>> movimentosFolha = new LinkedHashMap<>();
        TreeSet<String> chaves = new TreeSet<>();

        Map<String, List<MovAtrib>> porConta = itens.stream()
                .collect(Collectors.groupingBy(item -> item.atrib().contaKey(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<MovAtrib>> entrada : porConta.entrySet()) {
            String chave = entrada.getKey();
            List<FinanceiroMovimento> movimentos = entrada.getValue().stream().map(MovAtrib::mov).toList();
            BigDecimal soma = movimentos.stream().map(this::valorAssinado).reduce(BigDecimal.ZERO, BigDecimal::add);
            descricaoFolha.put(chave, entrada.getValue().get(0).atrib().contaDescricao());
            movimentosFolha.put(chave, movimentos);
            for (String prefixo : prefixos(chave)) {
                chaves.add(prefixo);
                valor.merge(prefixo, soma, BigDecimal::add);
                quantidade.merge(prefixo, movimentos.size(), Integer::sum);
            }
        }

        return chaves.stream()
                .filter(chave -> chavePai(chave) == null || !chaves.contains(chavePai(chave)))
                .sorted(Comparator.comparing(this::ordenacao))
                .map(chave -> no(chave, chaves, valor, quantidade, descricaoFolha, movimentosFolha, plano, receitaLiquida))
                .toList();
    }

    private DreContaNo no(String chave, TreeSet<String> chaves, Map<String, BigDecimal> valor,
            Map<String, Integer> quantidade, Map<String, String> descricaoFolha,
            Map<String, List<FinanceiroMovimento>> movimentosFolha, Map<String, PlanoConta> plano,
            BigDecimal receitaLiquida) {
        List<String> filhasChaves = chaves.stream()
                .filter(outra -> chave.equals(chavePai(outra)))
                .sorted(Comparator.comparing(this::ordenacao))
                .toList();
        boolean sintetica = !filhasChaves.isEmpty();
        String descricao = plano.containsKey(chave) ? plano.get(chave).descricao()
                : descricaoFolha.getOrDefault(chave, chave);
        BigDecimal v = valor.getOrDefault(chave, BigDecimal.ZERO);
        int q = quantidade.getOrDefault(chave, 0);
        BigDecimal pct = percentual(v, receitaLiquida);
        String codigo = chave.startsWith("~") ? "" : chave;
        int nivel = nivel(chave);
        if (sintetica) {
            List<DreContaNo> filhos = filhasChaves.stream()
                    .map(filha -> no(filha, chaves, valor, quantidade, descricaoFolha, movimentosFolha, plano, receitaLiquida))
                    .toList();
            return new DreContaNo(codigo, descricao, true, nivel, v, pct, q, List.of(), filhos);
        }
        List<DreOrigemResumo> origens = origens(movimentosFolha.getOrDefault(chave, List.of()));
        return new DreContaNo(codigo, descricao, false, nivel, v, pct, q, origens, List.of());
    }

    private List<DreOrigemResumo> origens(List<FinanceiroMovimento> movimentos) {
        Map<FinanceiroOrigemTipo, List<FinanceiroMovimento>> porOrigem = movimentos.stream()
                .collect(Collectors.groupingBy(FinanceiroMovimento::origemTipo, LinkedHashMap::new, Collectors.toList()));
        return porOrigem.entrySet().stream()
                .map(entrada -> new DreOrigemResumo(entrada.getKey().name(), labelOrigem(entrada.getKey()),
                        entrada.getValue().stream().map(this::valorAssinado).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entrada.getValue().size()))
                .sorted(Comparator.comparing((DreOrigemResumo origem) -> origem.valor().abs()).reversed())
                .toList();
    }

    private List<String> prefixos(String chave) {
        if (chave.startsWith("~")) {
            return List.of(chave);
        }
        String[] segmentos = chave.split("\\.");
        List<String> prefixos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String segmento : segmentos) {
            if (atual.length() > 0) {
                atual.append('.');
            }
            atual.append(segmento);
            prefixos.add(atual.toString());
        }
        return prefixos;
    }

    private String chavePai(String chave) {
        if (chave.startsWith("~")) {
            return null;
        }
        int ponto = chave.lastIndexOf('.');
        return ponto < 0 ? null : chave.substring(0, ponto);
    }

    private int nivel(String chave) {
        if (chave.startsWith("~")) {
            return 1;
        }
        return (int) chave.chars().filter(caractere -> caractere == '.').count() + 1;
    }

    private String ordenacao(String chave) {
        return chave.startsWith("~") ? "zzz" + chave : chave;
    }

    private String labelOrigem(FinanceiroOrigemTipo tipo) {
        return switch (tipo) {
            case NOTA_COMPRA_DUPLICATA -> "Nota de compra";
            case PAGAMENTO_CAIXA -> "Pagamento caixa";
            case CAIXA_DINHEIRO -> "Caixa (dinheiro)";
            case EXTRATO_AVULSO -> "Extrato bancario";
            case FATURA_BAIXA -> "Fatura baixada";
            case FATURA_ABERTA -> "Fatura aberta";
            case CTE_ABERTO -> "CT-e aberto";
            case CTE_EMITIDO -> "CT-e emitido";
            case NOTA_COMPRA_COMPETENCIA -> "Nota de compra (competencia)";
        };
    }

    private BigDecimal secaoValor(List<MovAtrib> itens) {
        return itens.stream().map(item -> valorAssinado(item.mov())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorAssinado(FinanceiroMovimento movimento) {
        return movimento.natureza() == FinanceiroNatureza.RECEITA ? movimento.valor() : movimento.valor().negate();
    }

    // ------------------------------------------------------------------------------------------------
    // Escala das secoes de despesa pelo peso do cliente
    // ------------------------------------------------------------------------------------------------

    private DreSecao escalarSecao(DreSecao secao, BigDecimal fator, BigDecimal receitaLiquida) {
        BigDecimal valor = escalar(secao.valor(), fator);
        List<DreContaNo> contas = secao.contas().stream()
                .map(conta -> escalarConta(conta, fator, receitaLiquida))
                .toList();
        return new DreSecao(secao.codigo(), secao.titulo(), valor, percentual(valor, receitaLiquida),
                secao.quantidade(), secao.despesa(), contas);
    }

    private DreContaNo escalarConta(DreContaNo conta, BigDecimal fator, BigDecimal receitaLiquida) {
        BigDecimal valor = escalar(conta.valor(), fator);
        List<DreOrigemResumo> origens = conta.origens().stream()
                .map(origem -> new DreOrigemResumo(origem.origemTipo(), origem.label(), escalar(origem.valor(), fator),
                        origem.quantidade()))
                .toList();
        List<DreContaNo> filhos = conta.filhos().stream()
                .map(filho -> escalarConta(filho, fator, receitaLiquida))
                .toList();
        return new DreContaNo(conta.classificacao(), conta.descricao(), conta.sintetica(), conta.nivel(), valor,
                percentual(valor, receitaLiquida), conta.quantidade(), origens, filhos);
    }

    private BigDecimal escalar(BigDecimal valor, BigDecimal fator) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------------------------------------

    private DreLinha calculada(String codigo, String titulo, BigDecimal valor, BigDecimal receitaLiquida) {
        return new DreLinha(codigo, titulo, valor, percentual(valor, receitaLiquida), 0, true, false);
    }

    private List<DreResumoLiquidez> resumos(BigDecimal receitaLiquida, BigDecimal margemBruta, BigDecimal ebitda,
            BigDecimal resultadoLiquido) {
        return List.of(
                new DreResumoLiquidez("Receita liquida caixa", receitaLiquida, "Receita recebida menos deducoes",
                        tom(receitaLiquida)),
                new DreResumoLiquidez("Margem bruta", margemBruta, "Receita liquida menos custos dos servicos",
                        tom(margemBruta)),
                new DreResumoLiquidez("EBITDA caixa", ebitda, "Resultado operacional antes de depreciacao e financeiro",
                        tom(ebitda)),
                new DreResumoLiquidez("Resultado liquido", resultadoLiquido, "Resultado gerencial de caixa no periodo",
                        tom(resultadoLiquido)));
    }

    private BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(new BigDecimal("100")).divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal pct(BigDecimal fracao) {
        return fracao.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarDriver(String driver) {
        if (driver == null) {
            return DRIVER_PESO;
        }
        return switch (driver.trim().toUpperCase(Locale.ROOT)) {
            case DRIVER_FRETE, "VALOR", "FRETE_VALOR" -> DRIVER_FRETE;
            case DRIVER_CTES, "CTE", "CONHECIMENTOS", "QTD_CTES" -> DRIVER_CTES;
            default -> DRIVER_PESO;
        };
    }

    private String normalizarRegime(String regime) {
        if (regime == null) {
            return REGIME_COMPETENCIA;
        }
        return REGIME_CAIXA.equalsIgnoreCase(regime.trim()) ? REGIME_CAIXA : REGIME_COMPETENCIA;
    }

    private String rotuloDriver(String driver) {
        return switch (driver) {
            case DRIVER_FRETE -> "Valor de frete";
            case DRIVER_CTES -> "Numero de CT-es";
            default -> "Peso (tonelada)";
        };
    }

    private String tom(BigDecimal valor) {
        return valor.signum() >= 0 ? "positivo" : "negativo";
    }

    private String normaliza(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    private record Atribuicao(String secao, String contaKey, String contaDescricao) {
    }

    private record MovAtrib(FinanceiroMovimento mov, Atribuicao atrib) {
    }

    private record Rateio(String driverEfetivo, Map<Integer, BigDecimal> receitaPorCliente,
            Map<Integer, String> nomePorCliente, Map<Integer, DreClienteDriver> driverPorCliente,
            BigDecimal receitaTotal, BigDecimal despesaTotal, BigDecimal transferenciaDespesa,
            Map<Integer, BigDecimal> pesoPorCliente, Map<Integer, BigDecimal> despesaPorCliente,
            BigDecimal toneladasTotal, int qtdCtesTotal, BigDecimal custoPorTonelada,
            BigDecimal toneladasNaoAtribuidas, BigDecimal despesaNaoAtribuida, List<String> alertas) {
    }
}
