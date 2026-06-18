package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.DreContaNo;
import br.com.salome.core.domain.financeiro.DreGerencialSnapshot;
import br.com.salome.core.domain.financeiro.DreLinha;
import br.com.salome.core.domain.financeiro.DreOrigemResumo;
import br.com.salome.core.domain.financeiro.DreResumoLiquidez;
import br.com.salome.core.domain.financeiro.DreSecao;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroGrupo;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Espelho do {@link DreGerencialService} (regime caixa, travado e validado) para o regime de
 * COMPETENCIA: a receita entra pela data de emissao do CT-e ({@code cteEmissao}) e a despesa de
 * nota de compra pela {@code dataEntrada} com rateio temporal; extrato, caixa e pagamento caixa
 * permanecem como no fluxo. O periodo e filtrado por {@code dataCompetencia} (e nao por dataBaixa)
 * e nao ha filtro por status. A classificacao por secao do plano de contas e a montagem da arvore
 * sao identicas ao caixa, copiadas aqui de proposito para nao tocar no servico de caixa.
 */
@Service
public class DreGerencialCompetenciaService {

    static final String RECEITA = "RECEITA";
    static final String DEDUCOES = "DEDUCOES";
    static final String CUSTOS_SERVICOS = "CUSTOS_SERVICOS";
    static final String DESPESAS_COMERCIAIS = "DESPESAS_COMERCIAIS";
    static final String DESPESAS_ADMINISTRATIVAS = "DESPESAS_ADMINISTRATIVAS";
    static final String DEPRECIACAO_AMORTIZACAO = "DEPRECIACAO_AMORTIZACAO";
    static final String RESULTADO_FINANCEIRO = "RESULTADO_FINANCEIRO";
    static final String IMPOSTOS = "IMPOSTOS";
    static final String TRANSFERENCIA = "TRANSFERENCIA";

    static final String CONTA_SEM_CC_KEY = "~SEM_CC";
    static final String CONTA_BANCARIAS_EXTRATO_KEY = "~BANCARIAS_EXTRATO";
    private static final String CONTA_SEM_CC = "Sem centro de custo";
    private static final String CONTA_BANCARIAS_EXTRATO = "Despesas Bancarias Extrato";

    private static final List<String> ORDEM_SECOES = List.of(RECEITA, DEDUCOES, CUSTOS_SERVICOS,
            DESPESAS_COMERCIAIS, DESPESAS_ADMINISTRATIVAS, DEPRECIACAO_AMORTIZACAO, RESULTADO_FINANCEIRO,
            IMPOSTOS, TRANSFERENCIA);

    private static final Map<String, String> TITULOS_SECAO = Map.ofEntries(
            Map.entry(RECEITA, "Receita operacional emitida"),
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
    public DreGerencialCompetenciaService(FinanceiroFluxoCaixaRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    DreGerencialCompetenciaService(FinanceiroFluxoCaixaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DreGerencialSnapshot dashboard(FinanceiroFiltro filtro, Integer filialId) {
        return dashboard(filtro, filialId, null);
    }

    public DreGerencialSnapshot dashboard(FinanceiroFiltro filtro, Integer filialId, String filial) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        List<FinanceiroMovimento> todos = repository.listarMovimentosCompetencia(normalizado).stream()
                .filter(movimento -> dentroPeriodoCompetencia(movimento, normalizado))
                .filter(movimento -> filialId == null || Objects.equals(movimento.filialId(), filialId))
                .filter(movimento -> filtraFilial(movimento, filial))
                .filter(movimento -> filtraBusca(movimento, normalizado.busca()))
                .toList();

        List<FinanceiroMovimento> receitasExcluidas = todos.stream()
                .filter(FinanceiroMovimento::receitaExcluida)
                .toList();
        BigDecimal receitasExcluidasValor = receitasExcluidas.stream()
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FinanceiroMovimento> movimentos = todos.stream()
                .filter(movimento -> !movimento.receitaExcluida())
                .filter(this::despesaPermitidaCompetencia)
                .toList();

        Map<String, PlanoConta> plano = repository.listarPlanoContas().stream()
                .collect(Collectors.toMap(PlanoConta::classificacao, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<MovAtrib> classificados = movimentos.stream()
                .map(movimento -> new MovAtrib(movimento, classificar(movimento, plano)))
                .toList();
        Map<String, List<MovAtrib>> porSecao = classificados.stream()
                .collect(Collectors.groupingBy(item -> item.atrib().secao(), LinkedHashMap::new, Collectors.toList()));

        BigDecimal receita = secaoValor(porSecao, RECEITA);
        BigDecimal deducoes = secaoValor(porSecao, DEDUCOES);
        BigDecimal receitaLiquida = receita.add(deducoes);
        BigDecimal custos = secaoValor(porSecao, CUSTOS_SERVICOS);
        BigDecimal margemBruta = receitaLiquida.add(custos);
        BigDecimal comerciais = secaoValor(porSecao, DESPESAS_COMERCIAIS);
        BigDecimal administrativas = secaoValor(porSecao, DESPESAS_ADMINISTRATIVAS);
        BigDecimal ebitda = margemBruta.add(comerciais).add(administrativas);
        BigDecimal depreciacao = secaoValor(porSecao, DEPRECIACAO_AMORTIZACAO);
        BigDecimal ebit = ebitda.add(depreciacao);
        BigDecimal financeiro = secaoValor(porSecao, RESULTADO_FINANCEIRO);
        BigDecimal impostos = secaoValor(porSecao, IMPOSTOS);
        BigDecimal resultadoLiquido = ebit.add(financeiro).add(impostos);

        List<DreSecao> secoes = new ArrayList<>();
        for (String codigo : ORDEM_SECOES) {
            List<MovAtrib> itens = porSecao.get(codigo);
            if (itens == null || itens.isEmpty()) {
                continue;
            }
            BigDecimal valor = secaoValor(porSecao, codigo);
            List<DreContaNo> contas = construirArvore(itens, plano, receitaLiquida);
            secoes.add(new DreSecao(codigo, TITULOS_SECAO.getOrDefault(codigo, codigo), valor,
                    percentual(valor, receitaLiquida), itens.size(), !RECEITA.equals(codigo), contas));
        }

        List<DreLinha> linhas = List.of(
                calculada("RECEITA_LIQUIDA", "Receita liquida competencia", receitaLiquida, receitaLiquida),
                calculada("MARGEM_BRUTA", "Margem bruta", margemBruta, receitaLiquida),
                calculada("EBITDA", "EBITDA competencia gerencial", ebitda, receitaLiquida),
                calculada("EBIT", "EBIT competencia gerencial", ebit, receitaLiquida),
                calculada("RESULTADO_LIQUIDO", "Resultado liquido gerencial de competencia", resultadoLiquido, receitaLiquida));

        List<MovAtrib> semCentroCusto = classificados.stream()
                .filter(item -> CONTA_SEM_CC_KEY.equals(item.atrib().contaKey()))
                .toList();

        BigDecimal custoTotalPeriodo = movimentos.stream()
                .filter(movimento -> movimento.natureza() == FinanceiroNatureza.DESPESA)
                .map(FinanceiroMovimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toneladasTransportadas = repository.somarToneladasTransportadas(normalizado);
        BigDecimal custoPorTonelada = toneladasTransportadas == null || toneladasTransportadas.signum() == 0
                ? BigDecimal.ZERO
                : custoTotalPeriodo.divide(toneladasTransportadas, 2, RoundingMode.HALF_UP);

        return new DreGerencialSnapshot(
                normalizado.inicio(),
                normalizado.fim(),
                Instant.now(clock),
                resumos(receitaLiquida, margemBruta, ebitda, resultadoLiquido),
                linhas,
                secoes,
                grupos(movimentos, FinanceiroMovimento::filial, 20),
                grupos(movimentos, FinanceiroMovimento::planoContas, 20),
                grupos(movimentos, FinanceiroMovimento::centroCusto, 20),
                movimentos.stream()
                        .sorted(Comparator.comparing(FinanceiroMovimento::dataCompetencia, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(FinanceiroMovimento::documento))
                        .toList(),
                alertas(semCentroCusto, receitasExcluidas, receitasExcluidasValor),
                receitasExcluidas.size(),
                receitasExcluidasValor,
                repository.demonstrativo(),
                custoTotalPeriodo,
                toneladasTransportadas == null ? BigDecimal.ZERO : toneladasTransportadas,
                custoPorTonelada);
    }

    /**
     * Regra exclusiva do regime de competencia para DESPESAS com plano de contas: so entram contas
     * com {@code planocontas.dmr = Sim} e cuja {@code classificacao} nao inicie com "1" (contas de
     * receita). Despesas sem plano de contas mantem o comportamento atual (Sem centro de custo ->
     * Administrativo; Despesas Bancarias Extrato -> Financeiro). Receitas ficam fora deste filtro.
     */
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

    private boolean dentroPeriodoCompetencia(FinanceiroMovimento movimento, FinanceiroFiltro filtro) {
        return movimento.dataCompetencia() != null
                && !movimento.dataCompetencia().isBefore(filtro.inicio())
                && !movimento.dataCompetencia().isAfter(filtro.fim());
    }

    private boolean filtraBusca(FinanceiroMovimento movimento, String busca) {
        if (busca == null || busca.isBlank()) {
            return true;
        }
        String needle = normaliza(busca);
        return List.of(movimento.filial(), movimento.clienteFornecedor(), movimento.banco(), movimento.centroCusto(),
                        movimento.planoContas(), movimento.dmr(), movimento.documento(), movimento.historico())
                .stream()
                .filter(Objects::nonNull)
                .map(this::normaliza)
                .anyMatch(valor -> valor.contains(needle));
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

    // ------------------------------------------------------------------------------------------------
    // Classificacao por secao (fiel ao legado) — copia do servico de caixa
    // ------------------------------------------------------------------------------------------------

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

    private String secaoPorDescricao(String classificacao, FinanceiroMovimento movimento,
            Map<String, PlanoConta> plano) {
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

    // ------------------------------------------------------------------------------------------------
    // Arvore sintetico -> analitico — copia do servico de caixa
    // ------------------------------------------------------------------------------------------------

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
            case NOTA_COMPRA_COMPETENCIA -> "Nota de compra (competencia)";
            case PAGAMENTO_CAIXA -> "Pagamento caixa";
            case CAIXA_DINHEIRO -> "Caixa (dinheiro)";
            case EXTRATO_AVULSO -> "Extrato bancario";
            case FATURA_BAIXA -> "Fatura baixada";
            case FATURA_ABERTA -> "Fatura aberta";
            case CTE_ABERTO -> "CT-e aberto";
            case CTE_EMITIDO -> "CT-e emitido";
        };
    }

    private BigDecimal secaoValor(Map<String, List<MovAtrib>> porSecao, String codigo) {
        return porSecao.getOrDefault(codigo, List.of()).stream()
                .map(item -> valorAssinado(item.mov()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorAssinado(FinanceiroMovimento movimento) {
        return movimento.natureza() == FinanceiroNatureza.RECEITA ? movimento.valor() : movimento.valor().negate();
    }

    private DreLinha calculada(String codigo, String titulo, BigDecimal valor, BigDecimal receitaLiquida) {
        return new DreLinha(codigo, titulo, valor, percentual(valor, receitaLiquida), 0, true, false);
    }

    private BigDecimal percentual(BigDecimal valor, BigDecimal receitaLiquida) {
        if (receitaLiquida == null || receitaLiquida.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(new BigDecimal("100")).divide(receitaLiquida, 2, RoundingMode.HALF_UP);
    }

    private List<DreResumoLiquidez> resumos(BigDecimal receitaLiquida, BigDecimal margemBruta, BigDecimal ebitda,
            BigDecimal resultadoLiquido) {
        return List.of(
                new DreResumoLiquidez("Receita liquida competencia", receitaLiquida, "Receita emitida menos deducoes",
                        tom(receitaLiquida)),
                new DreResumoLiquidez("Margem bruta", margemBruta, "Receita liquida menos custos dos servicos",
                        tom(margemBruta)),
                new DreResumoLiquidez("EBITDA competencia", ebitda, "Resultado operacional antes de depreciacao e financeiro",
                        tom(ebitda)),
                new DreResumoLiquidez("Resultado liquido", resultadoLiquido, "Resultado gerencial de competencia no periodo",
                        tom(resultadoLiquido)));
    }

    private List<String> alertas(List<MovAtrib> semCentroCusto, List<FinanceiroMovimento> receitasExcluidas,
            BigDecimal receitasExcluidasValor) {
        List<String> alertas = new ArrayList<>();
        if (!receitasExcluidas.isEmpty()) {
            alertas.add(receitasExcluidas.size() + " receita(s) removida(s) por banco 34/Perdas e Danos ou cliente Expresso Salome Ltda, total "
                    + receitasExcluidasValor + ".");
        }
        if (!semCentroCusto.isEmpty()) {
            BigDecimal valor = semCentroCusto.stream().map(item -> item.mov().valor())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            alertas.add(semCentroCusto.size() + " despesa(s) sem centro de custo lancada(s) em Administrativo, total "
                    + valor + ".");
        }
        if (repository.demonstrativo()) {
            alertas.add("Modo demonstrativo ativo: conecte o datasource legado para ver dados reais.");
        }
        return alertas;
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
}
