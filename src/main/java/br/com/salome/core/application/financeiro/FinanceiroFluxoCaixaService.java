package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.FinanceiroContaNo;
import br.com.salome.core.domain.financeiro.FinanceiroDashboardSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroGrupo;
import br.com.salome.core.domain.financeiro.FinanceiroHorizonteCard;
import br.com.salome.core.domain.financeiro.FinanceiroKpi;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroProjecaoPonto;
import br.com.salome.core.domain.financeiro.FinanceiroRetrospectivoCard;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Monta o dashboard estrategico do Fluxo de Caixa: cards de horizonte (a pagar / a receber por
 * vencimento a partir de hoje), projecao de saldo conciliada com o saldo bancario e cards
 * retrospectivos do periodo. O DRE permanece isolado (este service nao toca naquele dominio).
 */
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
        FinanceiroFiltro tela = filtro == null ? FinanceiroFiltro.padrao() : filtro.normalizado();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());

        // Carrega uma janela ampla o suficiente para capturar atrasados (passado) e o que vence ate
        // o fim do mes, alem do periodo da tela. Uma unica leitura serve a tudo.
        List<FinanceiroMovimento> todos = repository.listarMovimentos(janelaPrevisao(tela, hoje, fimMes)).stream()
                .filter(movimento -> !movimento.receitaExcluida())
                // Despesas "caixa 2" (duplicata No. Duplicata = "A VISTA") nao sao reais: ignoradas aqui,
                // isolado do DRE (regime caixa validado) que continua usando listarMovimentos sem este filtro.
                .filter(movimento -> !movimento.duplicataAVista())
                .toList();

        // Movimentos do periodo da tela: alimentam tabela, retrospectivo e rankings.
        List<FinanceiroMovimento> movimentosResumo = todos.stream()
                .filter(movimento -> intersectaPeriodo(movimento, tela))
                .filter(movimento -> filtraBusca(movimento, tela.busca()))
                .sorted(Comparator.comparing(FinanceiroMovimento::dataFluxo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FinanceiroMovimento::clienteFornecedor)
                        .thenComparing(FinanceiroMovimento::documento))
                .toList();

        List<FinanceiroMovimento> movimentosTabela = movimentosResumo.stream()
                .filter(movimento -> dentroPeriodoFluxo(movimento, tela))
                .filter(this::visivelNaTabela)
                .toList();

        List<FinanceiroSaldoBanco> saldosBancarios = repository.listarSaldosBancarios(tela);
        BigDecimal saldoBancarioAtual = saldosBancarios.stream()
                .map(FinanceiroSaldoBanco::saldoBancario)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, String> descricaoPlano = repository.listarPlanoContas().stream()
                .filter(plano -> plano.classificacao() != null && !plano.classificacao().isBlank())
                .collect(Collectors.toMap(PlanoConta::classificacao, PlanoConta::descricao, (a, b) -> a, LinkedHashMap::new));

        List<FinanceiroMovimento> previstos = todos.stream()
                .filter(movimento -> movimento.status() == FinanceiroStatus.PREVISTO)
                .filter(movimento -> movimento.dataVencimento() != null)
                .filter(movimento -> movimento.valor() != null && movimento.valor().signum() > 0)
                .toList();

        List<FinanceiroHorizonteCard> aPagar = horizontes(previstos, FinanceiroNatureza.DESPESA, hoje, fimMes, descricaoPlano);
        List<FinanceiroHorizonteCard> aReceber = horizontes(previstos, FinanceiroNatureza.RECEITA, hoje, fimMes, descricaoPlano);
        List<FinanceiroHorizonteCard> faturamentoPendente = faturamentoPendente(previstos, hoje, descricaoPlano);
        List<FinanceiroProjecaoPonto> projecao = projecao(previstos, hoje, fimMes, saldoBancarioAtual);
        List<FinanceiroRetrospectivoCard> retrospectivo = retrospectivo(movimentosResumo, tela, descricaoPlano);

        BigDecimal aReceberMes = valorDoHorizonte(aReceber, "MES");
        BigDecimal aPagarMes = valorDoHorizonte(aPagar, "MES");
        BigDecimal saldoProjetadoFimMes = projecao.isEmpty()
                ? saldoBancarioAtual
                : projecao.get(projecao.size() - 1).saldoProjetado();

        List<FinanceiroKpi> kpis = List.of(
                new FinanceiroKpi("Saldo bancario atual", saldoBancarioAtual,
                        "Saldo conciliado por banco (extrato)", tom(saldoBancarioAtual)),
                new FinanceiroKpi("A receber ate o fim do mes", aReceberMes,
                        "Faturas e CT-es a vencer de hoje ao fim do mes", "positivo"),
                new FinanceiroKpi("A pagar ate o fim do mes", aPagarMes,
                        "Contas a vencer de hoje ao fim do mes", "negativo"),
                new FinanceiroKpi("Saldo projetado fim do mes", saldoProjetadoFimMes,
                        "Saldo atual + a receber - a pagar (inclui atrasados)", tom(saldoProjetadoFimMes)));

        return new FinanceiroDashboardSnapshot(
                tela.inicio(),
                tela.fim(),
                Instant.now(clock),
                kpis,
                saldoBancarioAtual,
                aPagar,
                aReceber,
                faturamentoPendente,
                projecao,
                retrospectivo,
                grupos(movimentosResumo.stream()
                        .filter(movimento -> movimento.natureza() == FinanceiroNatureza.DESPESA)
                        .toList(), FinanceiroMovimento::centroCusto, 10),
                grupos(movimentosResumo.stream().filter(this::temBancoInformado)
                        .filter(movimento -> !ehFaturaReceita(movimento)).toList(), FinanceiroMovimento::banco, 8),
                saldosBancarios,
                grupos(movimentosResumo, FinanceiroMovimento::clienteFornecedor, 12),
                movimentosTabela,
                alertas(movimentosResumo, repository.demonstrativo()),
                repository.demonstrativo());
    }

    // ---------------------------------------------------------------------------------------------
    // Previsao futura: cards de horizonte e projecao de saldo
    // ---------------------------------------------------------------------------------------------

    private List<FinanceiroHorizonteCard> horizontes(List<FinanceiroMovimento> previstos, FinanceiroNatureza natureza,
            LocalDate hoje, LocalDate fimMes, Map<String, String> descricaoPlano) {
        LocalDate amanha = hoje.plusDays(1);
        LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return List.of(
                card("HOJE", "Hoje", natureza, hoje, hoje, previstos,
                        venc -> venc.equals(hoje), descricaoPlano),
                card("AMANHA", "Amanha", natureza, amanha, amanha, previstos,
                        venc -> venc.equals(amanha), descricaoPlano),
                card("SEMANA", "Esta semana", natureza, hoje, fimSemana, previstos,
                        venc -> !venc.isBefore(hoje) && !venc.isAfter(fimSemana), descricaoPlano),
                card("MES", "Este mes", natureza, hoje, fimMes, previstos,
                        venc -> !venc.isBefore(hoje) && !venc.isAfter(fimMes), descricaoPlano),
                cardMov("ATRASO", "Em atraso", natureza, null, hoje.minusDays(1), previstos,
                        movimento -> atrasoDoHorizonte(movimento, natureza, hoje), descricaoPlano));
    }

    /**
     * Card "Em atraso". Contas a pagar mantem o comportamento original (todos os vencidos). Contas a
     * receber passa a considerar somente faturas vencidas nos ultimos 30 dias ({@link #faturaAtrasadaRecente}):
     * faturas em cartorio (banco 31) e CT-es "a faturar" saem para o painel "Faturamento pendente".
     */
    private boolean atrasoDoHorizonte(FinanceiroMovimento movimento, FinanceiroNatureza natureza, LocalDate hoje) {
        LocalDate venc = movimento.dataVencimento();
        if (venc == null || !venc.isBefore(hoje)) {
            return false;
        }
        if (natureza == FinanceiroNatureza.RECEITA) {
            return faturaAtrasadaRecente(movimento, hoje);
        }
        return true;
    }

    private FinanceiroHorizonteCard card(String codigo, String titulo, FinanceiroNatureza natureza, LocalDate de,
            LocalDate ate, List<FinanceiroMovimento> previstos, Predicate<LocalDate> dentroDoVencimento,
            Map<String, String> descricaoPlano) {
        return cardMov(codigo, titulo, natureza, de, ate, previstos,
                movimento -> dentroDoVencimento.test(movimento.dataVencimento()), descricaoPlano);
    }

    private FinanceiroHorizonteCard cardMov(String codigo, String titulo, FinanceiroNatureza natureza, LocalDate de,
            LocalDate ate, List<FinanceiroMovimento> previstos, Predicate<FinanceiroMovimento> filtroMovimento,
            Map<String, String> descricaoPlano) {
        List<FinanceiroMovimento> doBucket = previstos.stream()
                .filter(movimento -> movimento.natureza() == natureza)
                .filter(filtroMovimento)
                .toList();
        BigDecimal valor = doBucket.stream().map(FinanceiroMovimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FinanceiroContaNo> contas = FinanceiroPlanoContasArvore.construir(doBucket, descricaoPlano);
        return new FinanceiroHorizonteCard(codigo, titulo, natureza, de, ate, valor, doBucket.size(),
                tomHorizonte(codigo, natureza, valor), contas);
    }

    /**
     * Painel "Faturamento pendente": separa o que falta faturar/cobrar e que hoje inflava o card "Em
     * atraso". Tres cards (todos RECEITA, com drill por plano de contas):
     * <ul>
     *   <li>CT-es a faturar (atrasados): CTE_ABERTO com fechamento vencido — deveriam ter sido faturados;</li>
     *   <li>Faturas atrasadas (todas): toda fatura em aberto vencida, inclusive as em cartorio (banco 31);</li>
     *   <li>CT-es aguardando prazo: CTE_ABERTO ainda dentro do prazo de faturamento.</li>
     * </ul>
     */
    private List<FinanceiroHorizonteCard> faturamentoPendente(List<FinanceiroMovimento> previstos, LocalDate hoje,
            Map<String, String> descricaoPlano) {
        return List.of(
                cardMov("CTE_A_FATURAR", "CT-es a faturar (atrasados)", FinanceiroNatureza.RECEITA, null,
                        hoje.minusDays(1), previstos,
                        movimento -> movimento.origemTipo() == FinanceiroOrigemTipo.CTE_ABERTO
                                && venceuAntesDeHoje(movimento, hoje), descricaoPlano),
                cardMov("FATURAS_ATRASADAS", "Faturas atrasadas (todas)", FinanceiroNatureza.RECEITA, null,
                        hoje.minusDays(1), previstos,
                        movimento -> (movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_ABERTA
                                || movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_CARTORIO)
                                && venceuAntesDeHoje(movimento, hoje), descricaoPlano),
                cardMov("CTE_AGUARDANDO", "CT-es aguardando prazo", FinanceiroNatureza.RECEITA, hoje, null,
                        previstos,
                        movimento -> movimento.origemTipo() == FinanceiroOrigemTipo.CTE_ABERTO
                                && !venceuAntesDeHoje(movimento, hoje), descricaoPlano));
    }

    /**
     * Curva diaria de saldo projetado, de hoje ate o fim do mes. Parte do saldo bancario atual e
     * acumula recebimentos previstos (entradas) menos pagamentos previstos (saidas). Os atrasados
     * (vencimento anterior a hoje) sao exigiveis e entram no primeiro ponto (hoje).
     */
    private List<FinanceiroProjecaoPonto> projecao(List<FinanceiroMovimento> previstos, LocalDate hoje,
            LocalDate fimMes, BigDecimal saldoBancarioAtual) {
        List<FinanceiroProjecaoPonto> pontos = new ArrayList<>();
        BigDecimal saldo = saldoBancarioAtual;
        for (LocalDate dia = hoje; !dia.isAfter(fimMes); dia = dia.plusDays(1)) {
            boolean primeiroDia = dia.equals(hoje);
            LocalDate ref = dia;
            Predicate<FinanceiroMovimento> noDia = movimento -> {
                LocalDate venc = movimento.dataVencimento();
                return venc.equals(ref) || (primeiroDia && venc.isBefore(hoje));
            };
            BigDecimal entradas = previstos.stream()
                    .filter(movimento -> movimento.natureza() == FinanceiroNatureza.RECEITA)
                    // So recebimentos cobraveis entram no caixa projetado: faturas a vencer ou vencidas
                    // ha ate 30 dias. CT-es "a faturar", faturas em cartorio e faturas vencidas ha mais
                    // de 30 dias ficam de fora (ver painel "Faturamento pendente").
                    .filter(movimento -> entraNaProjecaoReceber(movimento, hoje))
                    .filter(noDia)
                    .map(FinanceiroMovimento::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal saidas = previstos.stream()
                    .filter(movimento -> movimento.natureza() == FinanceiroNatureza.DESPESA)
                    .filter(noDia)
                    .map(FinanceiroMovimento::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            saldo = saldo.add(entradas).subtract(saidas);
            pontos.add(new FinanceiroProjecaoPonto(dia, entradas, saidas, saldo));
        }
        return pontos;
    }

    // ---------------------------------------------------------------------------------------------
    // Retrospectivo: o que de fato entrou/saiu no periodo do filtro
    // ---------------------------------------------------------------------------------------------

    private List<FinanceiroRetrospectivoCard> retrospectivo(List<FinanceiroMovimento> movimentos, FinanceiroFiltro tela,
            Map<String, String> descricaoPlano) {
        List<FinanceiroMovimento> realizados = movimentos.stream()
                .filter(movimento -> movimento.status() == FinanceiroStatus.REALIZADO)
                .filter(movimento -> noPeriodo(movimento.dataBaixa(), tela))
                .toList();
        return List.of(
                retrospectivoCard("RECEBIDO_CLIENTES", "Recebido de clientes", realizados,
                        movimento -> origemEh(movimento, "FATURA_BAIXA"), "positivo",
                        "Baixas de fatura no periodo", descricaoPlano),
                retrospectivoCard("PAGO_FORNECEDORES", "Pago a fornecedores", realizados,
                        movimento -> origemEh(movimento, "NOTA_COMPRA_DUPLICATA"), "negativo",
                        "Duplicatas pagas via banco", descricaoPlano),
                retrospectivoCard("PAGO_EXTRATO", "Pago via extrato bancario", realizados,
                        movimento -> origemEh(movimento, "EXTRATO_AVULSO"), "negativo",
                        "Tarifas, juros e debitos diretos", descricaoPlano),
                // Pagamento caixa (tela pagamento caixa) + caixa em especie: despesa diaria em dinheiro,
                // mostrada em destaque mas FORA do fluxo bancario/projecao (segue a logica do DRE caixa).
                retrospectivoCard("PAGO_CAIXA", "Pago em dinheiro (caixa)", realizados,
                        movimento -> origemEh(movimento, "CAIXA_DINHEIRO", "PAGAMENTO_CAIXA"), "alerta",
                        "Pagamento caixa e especie - fora do fluxo bancario", descricaoPlano));
    }

    private FinanceiroRetrospectivoCard retrospectivoCard(String codigo, String titulo,
            List<FinanceiroMovimento> realizados, Predicate<FinanceiroMovimento> filtro, String tom, String detalhe,
            Map<String, String> descricaoPlano) {
        List<FinanceiroMovimento> selecionados = realizados.stream().filter(filtro).toList();
        BigDecimal valor = selecionados.stream().map(FinanceiroMovimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        String tomFinal = valor.signum() == 0 ? "neutro" : tom;
        List<FinanceiroContaNo> contas = FinanceiroPlanoContasArvore.construir(selecionados, descricaoPlano);
        return new FinanceiroRetrospectivoCard(codigo, titulo, valor, selecionados.size(), detalhe, tomFinal, contas);
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private FinanceiroFiltro janelaPrevisao(FinanceiroFiltro tela, LocalDate hoje, LocalDate fimMes) {
        LocalDate lookback = hoje.minusMonths(6);
        LocalDate inicio = tela.inicio().isBefore(lookback) ? tela.inicio() : lookback;
        LocalDate fim = tela.fim().isAfter(fimMes) ? tela.fim() : fimMes;
        return new FinanceiroFiltro(inicio, fim, tela.busca(), "TODOS", "TODAS");
    }

    private BigDecimal valorDoHorizonte(List<FinanceiroHorizonteCard> cards, String codigo) {
        return cards.stream()
                .filter(card -> card.codigo().equals(codigo))
                .map(FinanceiroHorizonteCard::valor)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private boolean origemEh(FinanceiroMovimento movimento, String... tipos) {
        String nome = movimento.origemTipo().name();
        for (String tipo : tipos) {
            if (nome.equals(tipo)) {
                return true;
            }
        }
        return false;
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
        // Faturas em aberto agora trazem o banco de cobranca (para o drill), mas nao sao movimentos de
        // extrato: continuam fora da tabela de conciliacao, como antes.
        return (temBancoInformado(movimento) && !ehFaturaReceita(movimento))
                || movimento.origemTipo() == FinanceiroOrigemTipo.CTE_ABERTO;
    }

    private static boolean ehFaturaReceita(FinanceiroMovimento movimento) {
        return movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_ABERTA
                || movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_CARTORIO;
    }

    /**
     * CT-e em aberto cujo fechamento ja passou (vencimento no passado): "a faturar atrasado".
     * Aparece no card "Em atraso", mas nao entra na projecao de caixa (nao foi faturado). Apos a
     * correcao da previsao, todo CTE_ABERTO normal vence hoje ou no futuro; logo um CTE_ABERTO com
     * vencimento anterior a hoje e necessariamente um "a faturar".
     */
    private static boolean aFaturarAtrasado(FinanceiroMovimento movimento, LocalDate hoje) {
        return movimento.origemTipo() == FinanceiroOrigemTipo.CTE_ABERTO
                && movimento.dataVencimento() != null
                && movimento.dataVencimento().isBefore(hoje);
    }

    private static boolean venceuAntesDeHoje(FinanceiroMovimento movimento, LocalDate hoje) {
        return movimento.dataVencimento() != null && movimento.dataVencimento().isBefore(hoje);
    }

    /**
     * Fatura em aberto vencida nos ultimos 30 dias ({@code [hoje-30, hoje-1]}): o atraso recente e
     * cobravel que ainda conta na conciliacao e na projecao. Exclui cartorio (outro origemTipo) e
     * faturas vencidas ha mais de 30 dias.
     */
    private static boolean faturaAtrasadaRecente(FinanceiroMovimento movimento, LocalDate hoje) {
        LocalDate venc = movimento.dataVencimento();
        return movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_ABERTA
                && venc != null
                && venc.isBefore(hoje)
                && !venc.isBefore(hoje.minusDays(30));
    }

    /**
     * Recebimentos previstos que entram na projecao de caixa: faturas a vencer ou vencidas ha ate 30
     * dias. Ficam de fora os CT-es "a faturar" (nao faturados), as faturas em cartorio (banco 31) e as
     * faturas vencidas ha mais de 30 dias.
     */
    private static boolean entraNaProjecaoReceber(FinanceiroMovimento movimento, LocalDate hoje) {
        if (aFaturarAtrasado(movimento, hoje)) {
            return false;
        }
        if (movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_CARTORIO) {
            return false;
        }
        LocalDate venc = movimento.dataVencimento();
        return !(movimento.origemTipo() == FinanceiroOrigemTipo.FATURA_ABERTA
                && venc != null
                && venc.isBefore(hoje.minusDays(30)));
    }

    private boolean temBancoInformado(FinanceiroMovimento movimento) {
        return movimento.banco() != null && !"Nao informado".equals(movimento.banco());
    }

    private boolean noPeriodo(LocalDate data, FinanceiroFiltro filtro) {
        return data != null && !data.isBefore(filtro.inicio()) && !data.isAfter(filtro.fim());
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

    private List<String> alertas(List<FinanceiroMovimento> movimentosResumo, boolean demonstrativo) {
        List<String> alertas = new ArrayList<>();
        if (demonstrativo) {
            alertas.add("Modo demonstrativo ativo: dados de exemplo. Ligue o datasource legado para ver os lancamentos reais.");
        }
        long extratoAvulso = movimentosResumo.stream()
                .filter(movimento -> movimento.origemTipo().name().equals("EXTRATO_AVULSO")).count();
        if (extratoAvulso > 0) {
            alertas.add(extratoAvulso + " lancamentos vieram direto do extrato bancario.");
        }
        alertas.add("Transferencias entre contas (TED/TRANSF/DOC/PIX/TEV) sao ignoradas para nao inflar receitas e despesas.");
        alertas.add("Receitas do tomador Expresso Salome e do banco 34 foram removidas do painel.");
        return alertas;
    }

    private String tom(BigDecimal valor) {
        return valor.signum() >= 0 ? "positivo" : "negativo";
    }

    private String tomHorizonte(String codigo, FinanceiroNatureza natureza, BigDecimal valor) {
        if (valor.signum() == 0) {
            return "neutro";
        }
        if ("ATRASO".equals(codigo) || "CTE_A_FATURAR".equals(codigo) || "FATURAS_ATRASADAS".equals(codigo)) {
            return "alerta";
        }
        return natureza == FinanceiroNatureza.RECEITA ? "positivo" : "negativo";
    }

    // ---------------------------------------------------------------------------------------------
    // Drill lazy (mantido para compatibilidade dos endpoints existentes)
    // ---------------------------------------------------------------------------------------------

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
}
