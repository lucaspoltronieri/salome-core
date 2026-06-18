package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.DreContaNo;
import br.com.salome.core.domain.financeiro.DreFilialDetalhe;
import br.com.salome.core.domain.financeiro.DreFilialLinha;
import br.com.salome.core.domain.financeiro.DreFilialSnapshot;
import br.com.salome.core.domain.financeiro.DreLinha;
import br.com.salome.core.domain.financeiro.DreOrigemResumo;
import br.com.salome.core.domain.financeiro.DreResumoLiquidez;
import br.com.salome.core.domain.financeiro.DreSecao;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PedagioLancamento;
import br.com.salome.core.domain.financeiro.PlanoConta;
import br.com.salome.core.domain.financeiro.RepasseInterFilial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DRE por filial (onda 1): receita realizada por filial emissora menos a despesa direta da filial
 * (movimentos que ja carregam {@code filialId}) e o overhead rateado - o "bolo sem filial"
 * distribuido entre as filiais por receita. Por construcao a soma dos resultados por filial fecha
 * com o resultado liquido do DRE caixa do mesmo periodo (toda despesa ou e direta ou e overhead
 * totalmente redistribuido).
 *
 * <p>A classificacao por secao e a montagem da arvore sintetico/analitico sao copiadas de proposito
 * do {@link DreClienteService}/{@link DreGerencialService} para nao tocar no DRE caixa, que esta
 * validado e travado. As ondas seguintes acrescentam os custos diretos (Rpa, combustivel,
 * manutencao, parceiro), o centro de custo TRANSFERENCIA e o acerto inter-filial.
 */
@Service
public class DreFilialService {

    static final String RECEITA = "RECEITA";
    static final String DEDUCOES = "DEDUCOES";
    static final String CUSTOS_SERVICOS = "CUSTOS_SERVICOS";
    static final String DESPESAS_COMERCIAIS = "DESPESAS_COMERCIAIS";
    static final String DESPESAS_ADMINISTRATIVAS = "DESPESAS_ADMINISTRATIVAS";
    static final String DEPRECIACAO_AMORTIZACAO = "DEPRECIACAO_AMORTIZACAO";
    static final String RESULTADO_FINANCEIRO = "RESULTADO_FINANCEIRO";
    static final String IMPOSTOS = "IMPOSTOS";
    static final String TRANSFERENCIA = "TRANSFERENCIA";

    static final String REGIME_CAIXA = "CAIXA";
    static final String REGIME_COMPETENCIA = "COMPETENCIA";

    static final String CONTA_SEM_CC_KEY = "~SEM_CC";
    static final String CONTA_BANCARIAS_EXTRATO_KEY = "~BANCARIAS_EXTRATO";
    private static final String CONTA_SEM_CC = "Sem centro de custo";
    private static final String CONTA_BANCARIAS_EXTRATO = "Despesas Bancarias Extrato";

    // Filiais reais da empresa (idFilial -> id canonico reportavel). Maua (7) consolida em Osasco (1).
    // Qualquer outra idFilial e parceiro (entrega terceirizada, custo ja em Contas a Pagar) -> excluida.
    private static final int FILIAL_SPO = 1;
    private static final Map<Integer, Integer> FILIAL_CANON = Map.of(1, 1, 7, 1, 2, 2, 4, 4, 5, 5);
    private static final Map<Integer, String> FILIAL_NOME = Map.of(
            1, "Osasco (SPO)", 2, "S.J. Rio Preto (SJP)", 4, "Campinas (CAM)", 5, "Ribeirao Preto (RIB)");

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
    private final PedagioRepository pedagioRepository;
    private final Clock clock;

    @Autowired
    public DreFilialService(FinanceiroFluxoCaixaRepository repository, PedagioRepository pedagioRepository) {
        this(repository, pedagioRepository, Clock.systemDefaultZone());
    }

    DreFilialService(FinanceiroFluxoCaixaRepository repository, PedagioRepository pedagioRepository, Clock clock) {
        this.repository = repository;
        this.pedagioRepository = pedagioRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------
    // Ranking de filiais
    // ------------------------------------------------------------------------------------------------

    public DreFilialSnapshot dashboard(FinanceiroFiltro filtro, String regime, Double repassePercent) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        String regimeEfetivo = normalizarRegime(regime);
        BigDecimal percent = repassePercent(repassePercent);
        BigDecimal fracao = percent.movePointLeft(2);
        Map<String, PlanoConta> plano = planoMap();
        List<FinanceiroMovimento> movimentos = movimentosDoRegime(normalizado, regimeEfetivo);
        Agrupamento ag = agrupar(movimentos, plano);
        Map<Integer, BigDecimal> pedagioPorFilial = pedagioPorFilial(normalizado);
        RepasseFilial repasse = repassePorFilial(normalizado, fracao);
        TransferenciaFilial transferencia = transferenciaPorFilial(movimentos, normalizado);

        Set<Integer> filiais = new LinkedHashSet<>(ag.filiais());
        filiais.addAll(pedagioPorFilial.keySet());
        filiais.addAll(repasse.recebido().keySet());
        filiais.addAll(repasse.pago().keySet());
        filiais.addAll(transferencia.ajuste().keySet());

        String busca = normaliza(normalizado.busca());
        List<DreFilialLinha> linhas = new ArrayList<>();
        for (Integer id : filiais) {
            String nome = nomeFilial(id, ag);
            if (!busca.isBlank() && !normaliza(nome).contains(busca)) {
                continue;
            }
            BigDecimal receita = ag.receitaPorFilial().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal despesaDireta = ag.despesaDiretaPorFilial().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal overhead = ag.overheadPorFilial().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal despesaTotal = despesaDireta.add(overhead);
            BigDecimal resultado = receita.subtract(despesaTotal);
            BigDecimal pedagio = pedagioPorFilial.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal repRec = repasse.recebido().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal repPago = repasse.pago().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal transfAjuste = transferencia.ajuste().getOrDefault(id, BigDecimal.ZERO);
            BigDecimal resultadoAjustado = resultado.subtract(pedagio).add(repRec).subtract(repPago).add(transfAjuste);
            linhas.add(new DreFilialLinha(id, nome, receita, despesaDireta, overhead, despesaTotal, resultado,
                    percentual(resultado, receita), pedagio, repRec, repPago, transfAjuste, resultadoAjustado,
                    percentual(resultadoAjustado, receita)));
        }
        linhas.sort(Comparator.comparing(DreFilialLinha::resultadoAjustado, Comparator.reverseOrder()));

        BigDecimal despesaTotal = ag.despesaDiretaTotal().add(ag.overheadPool());
        BigDecimal resultadoTotal = ag.receitaTotal().subtract(despesaTotal);
        BigDecimal pedagioTotal = pedagioPorFilial.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resultadoAjustadoTotal = resultadoTotal.subtract(pedagioTotal);

        // Marcos consolidados (receita liquida -> resultado liquido) pela mesma classificacao por secao
        // do DRE gerencial, para que os cards do topo batam linha a linha com o gerencial. O overhead nao
        // entra como ajuste aqui (impostos/financeiro/seguro ja estao nas proprias secoes); por isso o
        // resultado liquido consolidado reconcilia com o resultadoTotal do rateio.
        MarcosConsolidados marcos = marcosConsolidados(movimentos, plano);

        return new DreFilialSnapshot(normalizado.inicio(), normalizado.fim(), Instant.now(clock), regimeEfetivo,
                ag.receitaTotal(), marcos.receitaLiquida(), marcos.margemBruta(), marcos.ebitda(),
                ag.despesaDiretaTotal(), ag.overheadPool(), despesaTotal, resultadoTotal,
                percentual(resultadoTotal, ag.receitaTotal()), pedagioTotal, repasse.total(), percent,
                transferencia.total(), resultadoAjustadoTotal, linhas.size(),
                linhas, alertasDashboard(ag, pedagioTotal, repasse.total(), percent, transferencia.total()),
                repository.demonstrativo());
    }

    // ------------------------------------------------------------------------------------------------
    // DRE detalhado de uma filial (secoes reais da filial + overhead rateado)
    // ------------------------------------------------------------------------------------------------

    public DreFilialDetalhe dreDaFilial(int idFilial, FinanceiroFiltro filtro, String regime, Double repassePercent) {
        FinanceiroFiltro normalizado = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        String regimeEfetivo = normalizarRegime(regime);
        BigDecimal percent = repassePercent(repassePercent);
        Map<String, PlanoConta> plano = planoMap();
        List<FinanceiroMovimento> movimentos = movimentosDoRegime(normalizado, regimeEfetivo);
        Agrupamento ag = agrupar(movimentos, plano);

        String nome = nomeFilial(idFilial, ag);
        BigDecimal receitaTotalFilial = ag.receitaPorFilial().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal despesaDiretaFilial = ag.despesaDiretaPorFilial().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal overhead = ag.overheadPorFilial().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal despesaTotal = despesaDiretaFilial.add(overhead);
        BigDecimal resultado = receitaTotalFilial.subtract(despesaTotal);
        RepasseFilial repasse = repassePorFilial(normalizado, percent.movePointLeft(2));
        TransferenciaFilial transferencia = transferenciaPorFilial(movimentos, normalizado);
        BigDecimal pedagio = pedagioPorFilial(normalizado).getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal repasseRecebido = repasse.recebido().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal repassePago = repasse.pago().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal transferenciaAjuste = transferencia.ajuste().getOrDefault(idFilial, BigDecimal.ZERO);
        BigDecimal resultadoAjustado = resultado.subtract(pedagio).add(repasseRecebido).subtract(repassePago)
                .add(transferenciaAjuste);

        List<FinanceiroMovimento> filialMovs = movimentos.stream()
                .filter(m -> {
                    if (m.natureza() == FinanceiroNatureza.RECEITA) {
                        return canonicalKey(m) == idFilial;
                    }
                    Atribuicao atrib = classificar(m, plano);
                    if (ehDespesaComum(atrib.secao(), atrib.contaDescricao())) {
                        return false; // despesa comum entra como rateio (marco), nao nas secoes diretas
                    }
                    Integer canon = canonical(m.filialId());
                    return canon != null && canon == idFilial;
                })
                .toList();

        Map<String, BigDecimal> soma = somasPorSecao(filialMovs, plano);
        BigDecimal receita = soma.getOrDefault(RECEITA, BigDecimal.ZERO);
        BigDecimal deducoes = soma.getOrDefault(DEDUCOES, BigDecimal.ZERO);
        BigDecimal receitaLiquida = receita.add(deducoes);
        BigDecimal custos = soma.getOrDefault(CUSTOS_SERVICOS, BigDecimal.ZERO);
        BigDecimal comerciais = soma.getOrDefault(DESPESAS_COMERCIAIS, BigDecimal.ZERO);
        BigDecimal administrativas = soma.getOrDefault(DESPESAS_ADMINISTRATIVAS, BigDecimal.ZERO);
        BigDecimal depreciacao = soma.getOrDefault(DEPRECIACAO_AMORTIZACAO, BigDecimal.ZERO);
        BigDecimal financeiro = soma.getOrDefault(RESULTADO_FINANCEIRO, BigDecimal.ZERO);
        BigDecimal impostos = soma.getOrDefault(IMPOSTOS, BigDecimal.ZERO);

        BigDecimal margemBruta = receitaLiquida.add(custos);
        BigDecimal ebitda = margemBruta.add(comerciais).add(administrativas);
        BigDecimal ebit = ebitda.add(depreciacao);
        BigDecimal resultadoDireto = ebit.add(financeiro).add(impostos);
        BigDecimal overheadAjuste = overhead.negate();
        BigDecimal resultadoLiquido = resultadoDireto.add(overheadAjuste);
        BigDecimal pedagioAjuste = pedagio.negate();
        BigDecimal repasseLiquido = repasseRecebido.subtract(repassePago);
        BigDecimal resultadoLiquidoAjustado = resultadoLiquido.add(pedagioAjuste).add(repasseLiquido)
                .add(transferenciaAjuste);

        List<DreSecao> secoes = new ArrayList<>();
        Map<String, DreSecao> porCodigo = construirSecoes(filialMovs, plano, receitaLiquida).stream()
                .collect(Collectors.toMap(DreSecao::codigo, s -> s, (a, b) -> a, LinkedHashMap::new));
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
                calculada("RESULTADO_DIRETO", "Resultado direto da filial", resultadoDireto, receitaLiquida),
                calculada("OVERHEAD_RATEADO", "Despesas comuns rateadas (impostos/financeiro/seguro)", overheadAjuste, receitaLiquida),
                calculada("RESULTADO_LIQUIDO", "Resultado liquido (reconcilia caixa)", resultadoLiquido, receitaLiquida),
                calculada("PEDAGIO", "Pedagio off-book (vale-pedagio por placa)", pedagioAjuste, receitaLiquida),
                calculada("REPASSE_RECEBIDO", "Repasse recebido de outras filiais", repasseRecebido, receitaLiquida),
                calculada("REPASSE_PAGO", "Repasse pago a outras filiais", repassePago.negate(), receitaLiquida),
                calculada("TRANSFERENCIA_PESO", "Ajuste CC Transferencia (por peso)", transferenciaAjuste, receitaLiquida),
                calculada("RESULTADO_AJUSTADO", "Resultado ajustado (pedagio + repasse + transferencia)",
                        resultadoLiquidoAjustado, receitaLiquida));

        List<String> alertas = new ArrayList<>();
        alertas.add("Despesa direta da filial: " + despesaDiretaFilial.setScale(2, RoundingMode.HALF_UP)
                + ". Despesas comuns rateadas (impostos/financeiro/seguro, por receita): "
                + overhead.setScale(2, RoundingMode.HALF_UP) + ".");
        alertas.addAll(alertasDashboard(ag, pedagio, repasse.total(), percent, transferencia.total()));

        return new DreFilialDetalhe(idFilial, nome, regimeEfetivo, normalizado.inicio(), normalizado.fim(),
                Instant.now(clock), receitaTotalFilial, despesaDiretaFilial, overhead, despesaTotal, resultado,
                percentual(resultado, receitaTotalFilial), pedagio, repasseRecebido, repassePago, transferenciaAjuste,
                resultadoAjustado, percentual(resultadoAjustado, receitaTotalFilial),
                resumos(receitaLiquida, margemBruta, ebitda, resultadoLiquido), linhas, secoes, alertas,
                repository.demonstrativo());
    }

    // ------------------------------------------------------------------------------------------------
    // Agrupamento por filial + rateio do overhead (bolo sem filial) por receita
    // ------------------------------------------------------------------------------------------------

    private Agrupamento agrupar(List<FinanceiroMovimento> movimentos, Map<String, PlanoConta> plano) {
        Map<Integer, BigDecimal> receitaPorFilial = new LinkedHashMap<>();
        Map<Integer, BigDecimal> despesaDiretaPorFilial = new LinkedHashMap<>();
        Map<Integer, String> nomePorFilial = new LinkedHashMap<>();
        BigDecimal overheadPool = BigDecimal.ZERO;
        BigDecimal transferenciaDespesa = BigDecimal.ZERO;

        for (FinanceiroMovimento m : movimentos) {
            if (m.natureza() == FinanceiroNatureza.RECEITA) {
                // Receita sempre cai numa das 4 filiais (CT-e e emitido por filial da empresa; Maua->Osasco).
                int id = canonicalKey(m);
                receitaPorFilial.merge(id, m.valor(), BigDecimal::add);
                nomePorFilial.putIfAbsent(id, FILIAL_NOME.getOrDefault(id, "Filial " + id));
                continue;
            }
            // Despesa: transferencia entre contas nao e P&L e fica fora do resultado (igual ao DRE caixa).
            Atribuicao atrib = classificar(m, plano);
            String secao = atrib.secao();
            if (TRANSFERENCIA.equals(secao)) {
                transferenciaDespesa = transferenciaDespesa.add(m.valor());
                continue;
            }
            // Despesas comuns (impostos, financeiras, seguro) e despesa sem filial/parceira -> rateadas por
            // receita entre as 4 filiais. As demais (pessoal/admin, frota/custos, comerciais) ficam diretas
            // na filial onde foram lancadas. Maua->Osasco.
            Integer canon = canonical(m.filialId());
            if (canon == null || ehDespesaComum(secao, atrib.contaDescricao())) {
                overheadPool = overheadPool.add(m.valor());
            } else {
                despesaDiretaPorFilial.merge(canon, m.valor(), BigDecimal::add);
                nomePorFilial.putIfAbsent(canon, FILIAL_NOME.getOrDefault(canon, "Filial " + canon));
            }
        }

        Set<Integer> filiais = new LinkedHashSet<>();
        filiais.addAll(receitaPorFilial.keySet());
        filiais.addAll(despesaDiretaPorFilial.keySet());
        if (overheadPool.signum() != 0 && filiais.isEmpty()) {
            filiais.add(FILIAL_SPO);
        }

        // Rateia o overhead por receita entre as 4 filiais; se ninguem tiver receita, joga no HQ (Osasco).
        LinkedHashMap<Integer, BigDecimal> receitaNum = new LinkedHashMap<>();
        for (Integer id : filiais) {
            receitaNum.put(id, receitaPorFilial.getOrDefault(id, BigDecimal.ZERO));
        }
        BigDecimal receitaDenom = receitaNum.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Integer, BigDecimal> overheadPorFilial;
        if (receitaDenom.signum() == 0) {
            overheadPorFilial = new LinkedHashMap<>();
            filiais.add(FILIAL_SPO);
            overheadPorFilial.put(FILIAL_SPO, overheadPool);
            nomePorFilial.putIfAbsent(FILIAL_SPO, FILIAL_NOME.get(FILIAL_SPO));
        } else {
            overheadPorFilial = ratear(overheadPool, receitaNum, receitaDenom);
        }

        BigDecimal receitaTotal = receitaPorFilial.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal despesaDiretaTotal = despesaDiretaPorFilial.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Agrupamento(filiais, receitaPorFilial, despesaDiretaPorFilial, overheadPorFilial, nomePorFilial,
                receitaTotal, despesaDiretaTotal, overheadPool, transferenciaDespesa);
    }

    /**
     * Marcos consolidados (todas as filiais) a partir das mesmas somas por secao do DRE gerencial.
     * A secao TRANSFERENCIA fica de fora (nao e P&L, igual ao DRE caixa), entao o resultado liquido
     * consolidado reconcilia com o {@code resultadoTotal} do rateio por filial.
     */
    private MarcosConsolidados marcosConsolidados(List<FinanceiroMovimento> movimentos, Map<String, PlanoConta> plano) {
        Map<String, BigDecimal> soma = somasPorSecao(movimentos, plano);
        BigDecimal receita = soma.getOrDefault(RECEITA, BigDecimal.ZERO);
        BigDecimal deducoes = soma.getOrDefault(DEDUCOES, BigDecimal.ZERO);
        BigDecimal receitaLiquida = receita.add(deducoes);
        BigDecimal custos = soma.getOrDefault(CUSTOS_SERVICOS, BigDecimal.ZERO);
        BigDecimal margemBruta = receitaLiquida.add(custos);
        BigDecimal comerciais = soma.getOrDefault(DESPESAS_COMERCIAIS, BigDecimal.ZERO);
        BigDecimal administrativas = soma.getOrDefault(DESPESAS_ADMINISTRATIVAS, BigDecimal.ZERO);
        BigDecimal ebitda = margemBruta.add(comerciais).add(administrativas);
        return new MarcosConsolidados(receitaLiquida, margemBruta, ebitda);
    }

    /** Rateia {@code total} pelos numeradores, jogando o residuo do arredondamento no maior. */
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

    private List<String> alertasDashboard(Agrupamento ag, BigDecimal pedagio, BigDecimal repasseTotal,
            BigDecimal percent, BigDecimal transferenciaTotal) {
        List<String> alertas = new ArrayList<>();
        if (ag.overheadPool().compareTo(new BigDecimal("0.01")) >= 0) {
            alertas.add("Despesas comuns (impostos, financeiras, seguro + sem filial) de "
                    + ag.overheadPool().setScale(2, RoundingMode.HALF_UP)
                    + " rateadas por receita entre as 4 filiais.");
        }
        if (ag.transferenciaDespesa().signum() != 0) {
            alertas.add("Transferencias de " + ag.transferenciaDespesa().setScale(2, RoundingMode.HALF_UP)
                    + " ficaram fora do resultado (nao entram no resultado, igual ao DRE caixa).");
        }
        if (pedagio != null && pedagio.abs().compareTo(new BigDecimal("0.01")) >= 0) {
            alertas.add("Pedagio (vale-pedagio por placa) de " + pedagio.setScale(2, RoundingMode.HALF_UP)
                    + " e custo off-book: NAO esta no DRE caixa. O Resultado reconcilia com o caixa; o "
                    + "Resultado ajustado ja desconta o pedagio.");
        }
        if (repasseTotal != null && repasseTotal.abs().compareTo(new BigDecimal("0.01")) >= 0) {
            alertas.add("Acerto inter-filial: " + percent.stripTrailingZeros().toPlainString()
                    + "% sobre o frete dos CT-es transferidos = " + repasseTotal.setScale(2, RoundingMode.HALF_UP)
                    + " (a emissora paga a entregadora). E zero-sum entre filiais, nao muda o resultado total.");
        }
        if (transferenciaTotal != null && transferenciaTotal.abs().compareTo(new BigDecimal("0.01")) >= 0) {
            alertas.add("Centro de custo TRANSFERENCIA de " + transferenciaTotal.setScale(2, RoundingMode.HALF_UP)
                    + " (ja no bolo) reapropriado entre as filiais por peso transportado. Zero-sum, nao muda o total.");
        }
        alertas.add("Onda 3: acerto inter-filial (repasse por % do frete), pedagio por filial e CC TRANSFERENCIA "
                + "por peso. Folha individualizada por motorista fica para a fase de DRE por viagem.");
        if (repository.demonstrativo()) {
            alertas.add("Modo demonstrativo ativo: conecte o datasource legado para ver dados reais.");
        }
        return alertas;
    }

    private static final BigDecimal REPASSE_PERCENT_PADRAO = new BigDecimal("30");

    private BigDecimal repassePercent(Double repassePercent) {
        if (repassePercent == null || repassePercent < 0) {
            return REPASSE_PERCENT_PADRAO;
        }
        return BigDecimal.valueOf(repassePercent);
    }

    /** Repasse recebido/pago por filial = percentual x frete dos CT-es transferidos (zero-sum). */
    private RepasseFilial repassePorFilial(FinanceiroFiltro filtro, BigDecimal fracao) {
        Map<Integer, BigDecimal> recebido = new LinkedHashMap<>();
        Map<Integer, BigDecimal> pago = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        if (fracao.signum() != 0) {
            for (RepasseInterFilial r : repository.listarRepasseTransferencia(filtro)) {
                if (r.frete() == null) {
                    continue;
                }
                Integer origem = canonical(r.origem());
                Integer destino = canonical(r.destino());
                // Repasse so entre filiais reais. Entrega por parceiro (destino nulo) nao remunera
                // (custo ja em Contas a Pagar); transferencia intra-grupo (ex.: Osasco<->Maua) tambem nao.
                if (origem == null || destino == null || origem.equals(destino)) {
                    continue;
                }
                BigDecimal valor = r.frete().multiply(fracao).setScale(2, RoundingMode.HALF_UP);
                if (valor.signum() == 0) {
                    continue;
                }
                pago.merge(origem, valor, BigDecimal::add);
                recebido.merge(destino, valor, BigDecimal::add);
                total = total.add(valor);
            }
        }
        return new RepasseFilial(recebido, pago, total);
    }

    /**
     * Apropria o custo do centro de custo TRANSFERENCIA (veiculos que so transferem entre filiais, ja
     * no bolo) entre as filiais por PESO transportado. {@code ajuste} = custo lancado na filial menos a
     * cota por peso (zero-sum); aplicado ao resultado faz cada filial arcar com a cota por peso.
     */
    private TransferenciaFilial transferenciaPorFilial(List<FinanceiroMovimento> movimentos, FinanceiroFiltro filtro) {
        Map<Integer, BigDecimal> lancado = new LinkedHashMap<>();
        for (FinanceiroMovimento m : movimentos) {
            if (m.natureza() != FinanceiroNatureza.DESPESA) {
                continue;
            }
            if (!normaliza(m.centroCusto()).contains("transfer")) {
                continue;
            }
            lancado.merge(canonicalKey(m), m.valor(), BigDecimal::add);
        }
        BigDecimal total = lancado.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return new TransferenciaFilial(Map.of(), total);
        }
        LinkedHashMap<Integer, BigDecimal> peso = new LinkedHashMap<>();
        repository.listarPesoPorFilial(filtro).forEach((k, v) -> {
            Integer canon = canonical(k);
            if (canon != null) {
                peso.merge(canon, v == null ? BigDecimal.ZERO : v, BigDecimal::add);
            }
        });
        BigDecimal denom = peso.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Integer, BigDecimal> cota = denom.signum() == 0 ? Map.of() : ratear(total, peso, denom);
        Map<Integer, BigDecimal> ajuste = new LinkedHashMap<>();
        Set<Integer> ids = new LinkedHashSet<>();
        ids.addAll(lancado.keySet());
        ids.addAll(cota.keySet());
        for (Integer id : ids) {
            ajuste.put(id, lancado.getOrDefault(id, BigDecimal.ZERO).subtract(cota.getOrDefault(id, BigDecimal.ZERO)));
        }
        return new TransferenciaFilial(ajuste, total);
    }

    /** Pedagio (passagem menos estorno) por filial no periodo, atribuido pela placa do veiculo. */
    private Map<Integer, BigDecimal> pedagioPorFilial(FinanceiroFiltro filtro) {
        List<PedagioLancamento> lancamentos = pedagioRepository.listar();
        if (lancamentos.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> placaFilial = repository.listarFilialPorPlaca();
        Map<Integer, BigDecimal> porFilial = new LinkedHashMap<>();
        for (PedagioLancamento p : lancamentos) {
            if (p.data() == null || p.data().isBefore(filtro.inicio()) || p.data().isAfter(filtro.fim())) {
                continue;
            }
            // Placa nao mapeada ou de filial parceira -> joga no HQ (Osasco); valores irrisorios.
            Integer canon = canonical(placaFilial.get(p.placa()));
            porFilial.merge(canon == null ? FILIAL_SPO : canon, p.valor(), BigDecimal::add);
        }
        return porFilial;
    }

    private String nomeFilial(int id, Agrupamento ag) {
        return FILIAL_NOME.getOrDefault(id, ag.nomePorFilial().getOrDefault(id, "Filial " + id));
    }

    // ------------------------------------------------------------------------------------------------
    // Filtro por regime (caixa espelha o DRE caixa; competencia espelha o DRE competencia)
    // ------------------------------------------------------------------------------------------------

    private List<FinanceiroMovimento> movimentosDoRegime(FinanceiroFiltro normalizado, String regime) {
        if (REGIME_COMPETENCIA.equals(regime)) {
            return repository.listarMovimentosCompetencia(normalizado).stream()
                    .filter(m -> dentroPeriodoCompetencia(m, normalizado))
                    .filter(m -> !m.receitaExcluida())
                    .filter(this::despesaPermitidaCompetencia)
                    .toList();
        }
        return repository.listarMovimentos(normalizado).stream()
                .filter(m -> m.status() == FinanceiroStatus.REALIZADO)
                .filter(m -> dentroPeriodoBaixa(m, normalizado))
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

    /** idFilial canonico (uma das 4 filiais reais) ou null se for parceiro/sem filial. Maua(7)->Osasco(1). */
    private Integer canonical(Integer idFilial) {
        return idFilial == null ? null : FILIAL_CANON.get(idFilial);
    }

    /** Como {@link #canonical} mas com fallback no HQ (Osasco) para receita/casos sem filial real. */
    private int canonicalKey(FinanceiroMovimento movimento) {
        Integer canon = canonical(movimento.filialId());
        return canon == null ? FILIAL_SPO : canon;
    }

    /**
     * Despesa "comum" a todas as filiais, rateada por receita: impostos, despesas financeiras e seguro
     * (seguro dentro de Administrativas). As demais despesas ficam diretas na filial onde foram lancadas.
     */
    private boolean ehDespesaComum(String secao, String contaDescricao) {
        if (IMPOSTOS.equals(secao) || RESULTADO_FINANCEIRO.equals(secao)) {
            return true;
        }
        return DESPESAS_ADMINISTRATIVAS.equals(secao) && normaliza(contaDescricao).contains("seguro");
    }

    private Map<String, PlanoConta> planoMap() {
        return repository.listarPlanoContas().stream()
                .collect(Collectors.toMap(PlanoConta::classificacao, p -> p, (a, b) -> a, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------------------------------------
    // Secoes (copiado de proposito do DRE caixa / DRE por cliente)
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
                new DreResumoLiquidez("Resultado liquido", resultadoLiquido, "Resultado gerencial da filial no periodo",
                        tom(resultadoLiquido)));
    }

    private BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(new BigDecimal("100")).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String normalizarRegime(String regime) {
        if (regime == null) {
            return REGIME_COMPETENCIA;
        }
        return REGIME_CAIXA.equalsIgnoreCase(regime.trim()) ? REGIME_CAIXA : REGIME_COMPETENCIA;
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

    private record Agrupamento(Set<Integer> filiais, Map<Integer, BigDecimal> receitaPorFilial,
            Map<Integer, BigDecimal> despesaDiretaPorFilial, Map<Integer, BigDecimal> overheadPorFilial,
            Map<Integer, String> nomePorFilial, BigDecimal receitaTotal, BigDecimal despesaDiretaTotal,
            BigDecimal overheadPool, BigDecimal transferenciaDespesa) {
    }

    private record RepasseFilial(Map<Integer, BigDecimal> recebido, Map<Integer, BigDecimal> pago,
            BigDecimal total) {
    }

    private record TransferenciaFilial(Map<Integer, BigDecimal> ajuste, BigDecimal total) {
    }

    private record MarcosConsolidados(BigDecimal receitaLiquida, BigDecimal margemBruta, BigDecimal ebitda) {
    }
}
