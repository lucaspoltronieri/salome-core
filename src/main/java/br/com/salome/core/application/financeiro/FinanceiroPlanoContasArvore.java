package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.FinanceiroContaDocumento;
import br.com.salome.core.domain.financeiro.FinanceiroContaNo;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta a arvore de plano de contas (sintetico -> analitico) para o drill dos cards de horizonte
 * do Fluxo de Caixa. Implementacao propria desta tela, isolada do DRE (que esta travado): agrupa
 * os movimentos pela {@code classificacao}, deriva os prefixos hierarquicos (ex.: "2", "2.08",
 * "2.08.001") e pendura os documentos nas folhas. Movimentos sem classificacao caem na sentinela
 * {@value #SENTINELA_SEM}.
 */
final class FinanceiroPlanoContasArvore {

    private static final String SENTINELA_SEM = "~SEM";

    private FinanceiroPlanoContasArvore() {
    }

    /**
     * @param movimentos movimentos ja filtrados para o bucket (horizonte + natureza)
     * @param descricaoPorClassificacao descricao do plano de contas por classificacao (sintetico e analitico)
     */
    static List<FinanceiroContaNo> construir(List<FinanceiroMovimento> movimentos,
            Map<String, String> descricaoPorClassificacao) {
        Map<String, No> nos = new LinkedHashMap<>();
        for (FinanceiroMovimento movimento : movimentos) {
            String chave = chaveConta(movimento);
            List<String> prefixos = prefixos(chave);
            for (int i = 0; i < prefixos.size(); i++) {
                String prefixo = prefixos.get(i);
                No no = nos.computeIfAbsent(prefixo, p -> new No(p, nivel(p)));
                no.valor = no.valor.add(movimento.valor());
                no.quantidade++;
            }
            No folha = nos.get(chave);
            folha.documentos.add(new FinanceiroContaDocumento(movimento.documento(), movimento.clienteFornecedor(),
                    movimento.filial(), movimento.dataVencimento(), movimento.valor(), movimento.origemTipo().name()));
        }

        // Liga filhos aos pais e coleta as raizes.
        List<No> raizes = new ArrayList<>();
        for (No no : nos.values()) {
            String pai = chavePai(no.chave);
            if (pai != null && nos.containsKey(pai)) {
                nos.get(pai).filhos.add(no);
            } else {
                raizes.add(no);
            }
        }
        return raizes.stream()
                .sorted(comparador())
                .map(no -> converter(no, descricaoPorClassificacao))
                .toList();
    }

    private static FinanceiroContaNo converter(No no, Map<String, String> descricoes) {
        List<FinanceiroContaNo> filhos = no.filhos.stream()
                .sorted(comparador())
                .map(filho -> converter(filho, descricoes))
                .toList();
        boolean sintetica = !filhos.isEmpty();
        return new FinanceiroContaNo(no.chave, descricao(no, descricoes), sintetica, no.nivel, no.valor,
                no.quantidade, List.copyOf(no.documentos), filhos);
    }

    private static String descricao(No no, Map<String, String> descricoes) {
        if (SENTINELA_SEM.equals(no.chave)) {
            return "Sem classificacao";
        }
        String descricao = descricoes.get(no.chave);
        if (descricao != null && !descricao.isBlank()) {
            return no.chave + " - " + descricao;
        }
        return no.chave;
    }

    private static Comparator<No> comparador() {
        return Comparator.comparing((No no) -> SENTINELA_SEM.equals(no.chave)).thenComparing(no -> no.chave);
    }

    private static String chaveConta(FinanceiroMovimento movimento) {
        String classificacao = movimento.classificacao();
        if (classificacao == null || classificacao.isBlank() || "Nao informado".equals(classificacao)) {
            return SENTINELA_SEM;
        }
        return classificacao.trim();
    }

    private static List<String> prefixos(String chave) {
        if (SENTINELA_SEM.equals(chave)) {
            return List.of(SENTINELA_SEM);
        }
        List<String> prefixos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String parte : chave.split("\\.")) {
            atual.append(atual.length() == 0 ? "" : ".").append(parte);
            prefixos.add(atual.toString());
        }
        return prefixos;
    }

    private static String chavePai(String chave) {
        int corte = chave.lastIndexOf('.');
        return corte < 0 ? null : chave.substring(0, corte);
    }

    private static int nivel(String chave) {
        if (SENTINELA_SEM.equals(chave)) {
            return 1;
        }
        return (int) chave.chars().filter(c -> c == '.').count() + 1;
    }

    /** Acumulador mutavel durante a montagem da arvore. */
    private static final class No {
        private final String chave;
        private final int nivel;
        private BigDecimal valor = BigDecimal.ZERO;
        private int quantidade;
        private final List<No> filhos = new ArrayList<>();
        private final List<FinanceiroContaDocumento> documentos = new ArrayList<>();

        private No(String chave, int nivel) {
            this.chave = chave;
            this.nivel = nivel;
        }
    }
}
