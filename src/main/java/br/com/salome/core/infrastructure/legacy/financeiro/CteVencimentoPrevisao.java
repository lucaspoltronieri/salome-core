package br.com.salome.core.infrastructure.legacy.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolve a(s) data(s) de vencimento PREVISTO de um CT-e emitido e ainda nao faturado, para a
 * previsao de "A receber" do Fluxo de Caixa.
 *
 * <p>Regra padrao "15+15" (usada quando o cliente nao tem excecao):
 * <ul>
 *   <li>CT-e emitido entre os dias 01 e 15 vence no dia 30 do mes (ou ultimo dia se for menor);</li>
 *   <li>CT-e emitido entre o dia 16 e o ultimo dia vence no dia 15 do mes seguinte.</li>
 * </ul>
 *
 * <p>Alem do padrao, ha um catalogo de EXCECOES por cliente (fechamento semanal/quinzenal +
 * prazo de vencimento, com ajustes para dia da semana ou dia do mes, e parcelamento em alguns
 * casos). Um CT-e pode gerar mais de uma {@link Parcela} (ex.: clientes 45/60/75 dias).
 *
 * <p>IMPORTANTE: este resolvedor afeta SOMENTE a previsao do Fluxo de Caixa (CT-es abertos).
 * Nao altera o DRE nem os recebimentos realizados.
 *
 * <p>As regras do catalogo sao a interpretacao das instrucoes do negocio e estao marcadas com
 * {@code // VALIDAR} onde houve ambiguidade. A identificacao do cliente hoje e por razao social
 * (texto normalizado); ha um ponto unico para acrescentar CNPJ por regra ({@code cnpjs}).
 */
public final class CteVencimentoPrevisao {

    /**
     * Uma parcela prevista de recebimento de um CT-e. {@code fechamento} e a data em que a fatura
     * seria emitida (usada para decidir se a previsao ainda e valida em {@link #prever}).
     */
    public record Parcela(LocalDate vencimento, BigDecimal valor, String rotulo, LocalDate fechamento) {
    }

    /**
     * Previsao final de um CT-e ja decidida em relacao a "hoje": ou e uma parcela normal a vencer,
     * ou (quando o fechamento ja passou e o CT-e segue sem fatura) e uma unica entrada
     * {@code aFaturarAtrasado} para o card "Em atraso".
     */
    public record Previsao(LocalDate vencimento, BigDecimal valor, String rotulo, boolean aFaturarAtrasado) {
    }

    /** Calculo das parcelas de um CT-e a partir da emissao e do valor total. */
    @FunctionalInterface
    private interface Regra {
        List<Parcela> parcelas(LocalDate emissao, BigDecimal valorTotal);
    }

    /**
     * Entrada do catalogo: casa por CNPJ (quando informado) OU quando a razao social normalizada
     * contem TODOS os {@code tokens}.
     */
    private record RegraCliente(List<String> tokens, Set<String> cnpjs, Regra regra) {
    }

    private final List<RegraCliente> catalogo = construirCatalogo();

    /**
     * Resolve as parcelas previstas. Se o cliente nao casar com nenhuma excecao, devolve uma unica
     * parcela com a regra padrao 15+15. Emissao/valor nulos resultam em lista vazia.
     */
    public List<Parcela> resolver(String razaoSocial, String cnpj, LocalDate emissao, BigDecimal valorTotal) {
        if (emissao == null || valorTotal == null) {
            return List.of();
        }
        String nome = normalizar(razaoSocial);
        String cnpjLimpo = somenteDigitos(cnpj);
        for (RegraCliente entrada : catalogo) {
            if (casa(entrada, nome, cnpjLimpo)) {
                return entrada.regra().parcelas(emissao, valorTotal);
            }
        }
        return List.of(new Parcela(vencimentoPadrao(emissao), valorTotal, null, fechamentoQuinzenal(emissao)));
    }

    /**
     * Decide a previsao de um CT-e em relacao a {@code hoje}. Se o fechamento (emissao da fatura)
     * ainda nao passou ({@code fechamento >= hoje}), devolve as parcelas normais a vencer. Se o
     * fechamento ja passou e o CT-e segue sem fatura, devolve UMA entrada "a faturar" (valor cheio,
     * data = fechamento) para o card "Em atraso" — fora da previsao normal e da projecao de caixa.
     */
    public List<Previsao> prever(String razaoSocial, String cnpj, LocalDate emissao, BigDecimal valorTotal,
            LocalDate hoje) {
        List<Parcela> parcelas = resolver(razaoSocial, cnpj, emissao, valorTotal);
        if (parcelas.isEmpty()) {
            return List.of();
        }
        LocalDate fechamento = parcelas.get(0).fechamento();
        if (fechamento != null && fechamento.isBefore(hoje)) {
            return List.of(new Previsao(fechamento, valorTotal, "a faturar", true));
        }
        return parcelas.stream()
                .map(p -> new Previsao(p.vencimento(), p.valor(), p.rotulo(), false))
                .toList();
    }

    /** Regra padrao 15+15. Publica/estatica para reuso e teste. */
    public static LocalDate vencimentoPadrao(LocalDate emissao) {
        if (emissao == null) {
            return null;
        }
        if (emissao.getDayOfMonth() <= 15) {
            return emissao.withDayOfMonth(Math.min(30, emissao.lengthOfMonth()));
        }
        return emissao.plusMonths(1).withDayOfMonth(15);
    }

    private boolean casa(RegraCliente entrada, String nome, String cnpjLimpo) {
        if (!cnpjLimpo.isEmpty() && entrada.cnpjs().contains(cnpjLimpo)) {
            return true;
        }
        if (entrada.tokens().isEmpty()) {
            return false;
        }
        for (String token : entrada.tokens()) {
            if (!nome.contains(token)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Catalogo de excecoes por cliente (VALIDAR cada linha com o negocio)
    // ------------------------------------------------------------------------------------------

    private static List<RegraCliente> construirCatalogo() {
        List<RegraCliente> regras = new ArrayList<>();

        // Ajofer - fechamento segunda, +21 dias, caindo na sexta.
        regras.add(cliente("AJOFER", semanalSnapSemana(DayOfWeek.MONDAY, 21, DayOfWeek.FRIDAY)));

        // Akzo Nobel (Maua/VR) - razao social "AKZO NOBEL LTDA": fechamento terca, +60 dias, dia 04.
        regras.add(cliente("AKZO", semanalSnapDiaMes(DayOfWeek.TUESDAY, 60, 4)));

        // Anneta - fechamento segunda, +21 dias.
        regras.add(cliente("ANNETA", semanal(DayOfWeek.MONDAY, 21)));

        // Dovac - fechamento quinzenal, parcelas em 45/60/75 dias.
        regras.add(cliente("DOVAC", quinzenalParcelas(45, 60, 75)));

        // Eucatex - fechamento segunda, +21 dias.
        regras.add(cliente("EUCATEX", semanal(DayOfWeek.MONDAY, 21)));

        // Luksnova - fechamento quinzenal, parcelas em 45/60/75 dias.
        regras.add(cliente("LUKSNOVA", quinzenalParcelas(45, 60, 75)));

        // Maziero - fechamento segunda, +21 dias.
        regras.add(cliente("MAZIERO", semanal(DayOfWeek.MONDAY, 21)));

        // Sherwin (Sumare/Taboao) - fechamento segunda, +28 dias, caindo na sexta.
        regras.add(cliente("SHERWIN", semanalSnapSemana(DayOfWeek.MONDAY, 28, DayOfWeek.FRIDAY)));

        // Tintas Alessi - fechamento segunda, +21 dias.
        regras.add(cliente("ALESSI", semanal(DayOfWeek.MONDAY, 21)));

        // Tintas Iquine - fechamento quarta, +21 dias, sempre na quarta.
        regras.add(cliente("IQUINE", semanalSnapSemana(DayOfWeek.WEDNESDAY, 21, DayOfWeek.WEDNESDAY)));

        // Universo Tintas - fechamento segunda, +21 dias.
        regras.add(cliente("UNIVERSO", semanal(DayOfWeek.MONDAY, 21)));

        // Anjo - fechamento semanal (segunda); vencimento dia 15 ou dia 1 conforme semana do mes.
        regras.add(cliente("ANJO", CteVencimentoPrevisao::regraAnjo));

        // Braslatex e Romabor - fechamento segunda, +15 dias, vencendo na segunda mais proxima.
        regras.add(cliente("BRASLATEX", semanalSnapSemana(DayOfWeek.MONDAY, 15, DayOfWeek.MONDAY)));
        regras.add(cliente("ROMABOR", semanalSnapSemana(DayOfWeek.MONDAY, 15, DayOfWeek.MONDAY)));

        // Braile - fechamento quinzenal: 1a quinzena vence dia 15 do mes seguinte; 2a quinzena vence
        // dia 30 (ultimo) do mes seguinte. Uma parcela.
        regras.add(cliente("BRAILE", CteVencimentoPrevisao::regraBraile));

        return regras;
    }

    /** Atalho: regra por uma unica palavra-chave da razao social, sem CNPJ cadastrado ainda. */
    private static RegraCliente cliente(String token, Regra regra) {
        return new RegraCliente(List.of(token), Set.of(), regra); // VALIDAR: acrescentar CNPJ/idCliente
    }

    // ------------------------------------------------------------------------------------------
    // Formatos de regra reutilizaveis
    // ------------------------------------------------------------------------------------------

    /** Fechamento no proximo {@code fech} >= emissao; vencimento = fechamento + {@code dias}. */
    private static Regra semanal(DayOfWeek fech, int dias) {
        return (emissao, valor) -> {
            LocalDate fechamento = proximoOuMesmo(emissao, fech);
            return umaParcela(fechamento, fechamento.plusDays(dias), valor);
        };
    }

    /** Igual a {@link #semanal}, mas empurrando o vencimento para o proximo dia da semana {@code snap}. */
    private static Regra semanalSnapSemana(DayOfWeek fech, int dias, DayOfWeek snap) {
        return (emissao, valor) -> {
            LocalDate fechamento = proximoOuMesmo(emissao, fech);
            return umaParcela(fechamento, ajustarParaDiaSemana(fechamento.plusDays(dias), snap), valor);
        };
    }

    /** Igual a {@link #semanal}, mas empurrando o vencimento para o proximo dia-do-mes {@code diaMes}. */
    private static Regra semanalSnapDiaMes(DayOfWeek fech, int dias, int diaMes) {
        return (emissao, valor) -> {
            LocalDate fechamento = proximoOuMesmo(emissao, fech);
            return umaParcela(fechamento, ajustarParaDiaDoMes(fechamento.plusDays(dias), diaMes), valor);
        };
    }

    /** Fechamento quinzenal (dia 15 ou ultimo dia do mes) e uma parcela por prazo em {@code dias}. */
    private static Regra quinzenalParcelas(int... dias) {
        return (emissao, valor) -> {
            LocalDate base = fechamentoQuinzenal(emissao);
            List<BigDecimal> partes = dividir(valor, dias.length);
            List<Parcela> parcelas = new ArrayList<>();
            for (int i = 0; i < dias.length; i++) {
                parcelas.add(new Parcela(base.plusDays(dias[i]), partes.get(i),
                        "parc. " + (i + 1) + "/" + dias.length, base));
            }
            return parcelas;
        };
    }

    /**
     * Anjo: fechamento semanal (segunda) dos CT-es emitidos na semana. Pela semana do mes da
     * emissao: 1a semana -> dia 15 do mes corrente; 2a/3a semana -> dia 1 do mes seguinte; 4a
     * semana (ou mais) -> dia 15 do mes seguinte. Mira media de 10-15 dias a partir do fechamento.
     * VALIDAR: base da semana (emissao x segunda do fechamento).
     */
    private static List<Parcela> regraAnjo(LocalDate emissao, BigDecimal valor) {
        LocalDate fechamento = proximoOuMesmo(emissao, DayOfWeek.MONDAY);
        int semana = (emissao.getDayOfMonth() - 1) / 7 + 1;
        LocalDate vencimento;
        if (semana == 1) {
            vencimento = comDia(emissao, 15);
        } else if (semana == 2 || semana == 3) {
            vencimento = comDia(emissao.plusMonths(1), 1);
        } else {
            vencimento = comDia(emissao.plusMonths(1), 15);
        }
        return umaParcela(fechamento, vencimento, valor);
    }

    /**
     * Braile: fechamento quinzenal. Emissao do dia 1 ao 15 vence no dia 15 do mes seguinte; emissao
     * do dia 16 ao ultimo dia vence no dia 30 (ultimo) do mes seguinte. Uma unica parcela
     * (media ~30 dias a partir do fechamento).
     */
    private static List<Parcela> regraBraile(LocalDate emissao, BigDecimal valor) {
        LocalDate proximoMes = emissao.plusMonths(1);
        int dia = emissao.getDayOfMonth() <= 15 ? 15 : 30;
        return umaParcela(fechamentoQuinzenal(emissao), comDia(proximoMes, dia), valor);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers de data e valor
    // ------------------------------------------------------------------------------------------

    private static List<Parcela> umaParcela(LocalDate fechamento, LocalDate vencimento, BigDecimal valor) {
        return List.of(new Parcela(vencimento, valor, null, fechamento));
    }

    /** Primeiro {@code alvo} (dia da semana) >= base. */
    private static LocalDate proximoOuMesmo(LocalDate base, DayOfWeek alvo) {
        return base.with(TemporalAdjusters.nextOrSame(alvo));
    }

    /** Ajusta para o {@code alvo} (dia da semana) MAIS PROXIMO; empate vai para frente. */
    private static LocalDate ajustarParaDiaSemana(LocalDate base, DayOfWeek alvo) {
        LocalDate frente = base.with(TemporalAdjusters.nextOrSame(alvo));
        LocalDate tras = base.with(TemporalAdjusters.previousOrSame(alvo));
        long distFrente = ChronoUnit.DAYS.between(base, frente);
        long distTras = ChronoUnit.DAYS.between(tras, base);
        return distFrente <= distTras ? frente : tras;
    }

    /** Ajusta para o dia-do-mes {@code dia} MAIS PROXIMO (mes anterior, corrente ou seguinte). */
    private static LocalDate ajustarParaDiaDoMes(LocalDate base, int dia) {
        LocalDate melhor = null;
        long melhorDist = Long.MAX_VALUE;
        for (LocalDate candidato : List.of(
                comDia(base.minusMonths(1), dia), comDia(base, dia), comDia(base.plusMonths(1), dia))) {
            long dist = Math.abs(ChronoUnit.DAYS.between(base, candidato));
            if (dist < melhorDist) {
                melhorDist = dist;
                melhor = candidato;
            }
        }
        return melhor;
    }

    /** Fechamento quinzenal: dia 15 se a emissao for ate o dia 15, senao o ultimo dia do mes. */
    private static LocalDate fechamentoQuinzenal(LocalDate emissao) {
        if (emissao.getDayOfMonth() <= 15) {
            return comDia(emissao, 15);
        }
        return emissao.withDayOfMonth(emissao.lengthOfMonth());
    }

    private static LocalDate comDia(LocalDate base, int dia) {
        return base.withDayOfMonth(Math.min(dia, base.lengthOfMonth()));
    }

    /** Divide o total em {@code n} partes de 2 casas; a ultima absorve o arredondamento. */
    private static List<BigDecimal> dividir(BigDecimal total, int n) {
        BigDecimal parte = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        List<BigDecimal> partes = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < n - 1; i++) {
            partes.add(parte);
            acumulado = acumulado.add(parte);
        }
        partes.add(total.subtract(acumulado));
        return partes;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase()
                .trim();
    }

    private static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
