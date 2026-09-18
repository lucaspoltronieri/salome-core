package br.com.salome.core.domain.hubcrm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regra de amarração CT-e → cotação para a aprovação automática
 * (docs/crm/regra-amarracao-cotacao-cte.md).
 *
 * <ul>
 *   <li>pagador igual (obrigatório); vale a mesma raiz de CNPJ (matriz/filial);</li>
 *   <li>CT-e emitido no dia da cotação ou até {@code windowDays} dias depois;</li>
 *   <li>peso e frete iguais (tolerância de arredondamento). Peso até 5% diferente passa se o
 *       frete da cotação, proporcional ao peso do CT-e, bater;</li>
 *   <li>CT-e sem coleta (emitido no balcão) e cotação com coleta: compara o frete da
 *       cotação sem a taxa de coleta;</li>
 *   <li>valor da NF igual, ou diferente com peso e frete batendo: a NF final da carga muda e o
 *       frete (ad valorem) quase não mexe; nesse caso a cotação é aprovada e fica com os
 *       valores do CT-e ({@link Match#syncValues()});</li>
 *   <li>exceção: cotação com cubagem e CT-e com frete menor (cubagem esquecida na
 *       emissão) aprova se peso e NF baterem; a cotação não é
 *       alterada, porque o erro está no CT-e.</li>
 * </ul>
 * Remetente, destinatário, volumes e natureza não são critério; divergências ficam só
 * registradas. Cotação ABERTA de qualquer responsável é elegível; NÃO APROVADA só das
 * responsáveis que estão no ArpaSuite (Fernanda/Jaci), para reverter a perda.
 */
public final class CteQuoteMatcher {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal PERCENT = new BigDecimal("0.01");
    /** Diferença de peso aceita quando o frete acompanha o peso real (decisão do Lucas, caso 15839). */
    static final BigDecimal WEIGHT_PERCENT = new BigDecimal("0.05");

    public enum Outcome { APROVAR, SEM_COTACAO, AMBIGUO }

    /** {@code tiedQuoteIds}: no AMBIGUO, as cotações empatadas (têm CT-e, só não se sabe qual é a certa). */
    /** {@code syncValues}: aprovou com diferença de valores e a cotação deve ficar igual ao CT-e. */
    public record Match(Outcome outcome, LegacyQuote quote, String criteria, String divergences,
            List<Long> tiedQuoteIds, boolean syncValues) {}

    private record Candidate(LegacyQuote quote, BigDecimal freightDiff, boolean cubageException,
            boolean invoiceDiverges, String adjustments) {}

    private CteQuoteMatcher() {}

    public static Match evaluate(LegacyCte cte, List<LegacyQuote> quotes, int windowDays) {
        List<Candidate> candidates = new ArrayList<>();
        for (LegacyQuote quote : quotes) {
            candidate(cte, quote, windowDays).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) return new Match(Outcome.SEM_COTACAO, null, null, null, List.of(), false);

        // Mais próxima da emissão primeiro; depois a de NF igual; depois a de frete mais parecido; depois a mais nova.
        Comparator<Candidate> order = Comparator
                .comparing((Candidate c) -> c.quote().createdDate(), Comparator.reverseOrder())
                .thenComparing(Candidate::invoiceDiverges)
                .thenComparing(Candidate::freightDiff)
                .thenComparing(c -> c.quote().id(), Comparator.reverseOrder());
        candidates.sort(order);
        Candidate best = candidates.getFirst();
        if (candidates.size() > 1) {
            Candidate second = candidates.get(1);
            if (second.quote().createdDate().equals(best.quote().createdDate())
                    && second.invoiceDiverges() == best.invoiceDiverges()
                    && second.freightDiff().compareTo(best.freightDiff()) == 0) {
                List<Long> tied = candidates.stream()
                        .filter(c -> c.quote().createdDate().equals(best.quote().createdDate())
                                && c.invoiceDiverges() == best.invoiceDiverges()
                                && c.freightDiff().compareTo(best.freightDiff()) == 0)
                        .map(c -> c.quote().id())
                        .toList();
                List<String> ids = tied.stream().map(String::valueOf).toList();
                return new Match(Outcome.AMBIGUO, best.quote(), "Cotações empatadas: " + String.join(", ", ids),
                        null, tied, false);
            }
        }
        return new Match(Outcome.APROVAR, best.quote(), criteria(cte, best), divergences(cte, best.quote()),
                List.of(), !best.cubageException() && differs(cte, best.quote()));
    }

    static Optional<Candidate> candidate(LegacyCte cte, LegacyQuote quote, int windowDays) {
        if (!eligibleStatus(quote)) return Optional.empty();
        if (quote.createdDate() == null || cte.issueDate() == null) return Optional.empty();
        long days = ChronoUnit.DAYS.between(quote.createdDate(), cte.issueDate());
        if (days < 0 || days > windowDays) return Optional.empty();
        if (!samePayer(cte.payerCnpj(), quote.payerCnpj())) return Optional.empty();
        boolean weightOk = same(cte.weight(), quote.weight());
        if (!weightOk && !withinPercent(cte.weight(), quote.weight(), WEIGHT_PERCENT)) return Optional.empty();
        BigDecimal cteFreight = zero(cte.totalFreight());
        List<String> adjustments = new ArrayList<>();
        BigDecimal quoteFreight = zero(quote.totalFreight());
        if (withoutPickup(cte, quote)) {
            // Mercadoria entregue no balcão: o CT-e não cobra a coleta que a cotação previa.
            quoteFreight = freightWithoutPickup(quote);
            adjustments.add("sem coleta no CT-e (cotação previa " + money(quote.pickup()) + ")");
        }
        if (!weightOk) {
            // Peso um pouco diferente: o frete da cotação acompanha o peso real.
            quoteFreight = quoteFreight.multiply(zero(cte.weight()))
                    .divide(zero(quote.weight()), 2, RoundingMode.HALF_UP);
            adjustments.add("peso " + plain(cte.weight()) + " x " + plain(quote.weight())
                    + " kg, frete proporcional " + money(quoteFreight));
        }
        boolean freightOk = same(cteFreight, quoteFreight);
        boolean cubageException = !freightOk && positive(quote.cubage())
                && cteFreight.signum() > 0 && cteFreight.compareTo(quoteFreight) < 0;
        if (!freightOk && !cubageException) return Optional.empty();
        boolean invoiceDiverges = !same(cte.invoiceValue(), quote.invoiceValue());
        // NF diferente só passa quando o frete bate; na exceção da cubagem a NF tem de bater.
        if (invoiceDiverges && !freightOk) return Optional.empty();
        return Optional.of(new Candidate(quote, cteFreight.subtract(quoteFreight).abs(), cubageException,
                invoiceDiverges, String.join("; ", adjustments)));
    }

    /**
     * Mesmo pagador: CNPJ igual ou, para CNPJ, a mesma raiz (8 primeiros dígitos, matriz e
     * filiais da mesma empresa, com a mesma razão social). CPF só vale igual.
     */
    static boolean samePayer(String cteCnpj, String quoteCnpj) {
        if (blank(cteCnpj) || blank(quoteCnpj)) return false;
        String a = digits(cteCnpj);
        String b = digits(quoteCnpj);
        if (a.equals(b)) return true;
        return a.length() == 14 && b.length() == 14 && a.substring(0, 8).equals(b.substring(0, 8));
    }

    /** Chave para agrupar as cotações candidatas de um pagador (raiz do CNPJ). */
    public static String payerKey(String cnpj) {
        String value = digits(cnpj);
        return value.length() == 14 ? value.substring(0, 8) : value;
    }

    /** Cotação com taxa de coleta e CT-e sem coleta (emitido direto no balcão). */
    static boolean withoutPickup(LegacyCte cte, LegacyQuote quote) {
        return positive(quote.pickup()) && cte.charges() != null && zero(cte.charges().pickup()).signum() == 0;
    }

    /** Frete da cotação sem a coleta, recalculando o ICMS "por dentro" na mesma alíquota. */
    static BigDecimal freightWithoutPickup(LegacyQuote quote) {
        BigDecimal total = zero(quote.totalFreight());
        BigDecimal icms = zero(quote.icms());
        BigDecimal base = total.subtract(icms).subtract(zero(quote.pickup()));
        if (total.signum() <= 0 || icms.signum() <= 0) return base.max(BigDecimal.ZERO);
        BigDecimal rate = icms.divide(total, 6, RoundingMode.HALF_UP);
        return base.divide(BigDecimal.ONE.subtract(rate), 2, RoundingMode.HALF_UP);
    }

    static boolean withinPercent(BigDecimal left, BigDecimal right, BigDecimal percent) {
        BigDecimal a = zero(left);
        BigDecimal b = zero(right);
        if (a.signum() <= 0 || b.signum() <= 0) return false;
        return a.subtract(b).abs().compareTo(a.max(b).multiply(percent)) <= 0;
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    static boolean eligibleStatus(LegacyQuote quote) {
        String status = HubCrmNormalization.normalizedText(quote.status());
        if ("ABERTA".equals(status)) return true;
        return "NAO APROVADA".equals(status) && inArpaSuite(quote.responsible());
    }

    public static boolean inArpaSuite(String responsible) {
        String name = HubCrmNormalization.normalizedText(responsible);
        return name.contains("FERNANDA") || name.contains("JACI") || name.contains("QUEIROZ");
    }

    /** Algum valor da cotação (peso, NF ou frete) difere do CT-e além do centavo. */
    static boolean differs(LegacyCte cte, LegacyQuote quote) {
        return zero(cte.weight()).compareTo(zero(quote.weight())) != 0
                || zero(cte.invoiceValue()).compareTo(zero(quote.invoiceValue())) != 0
                || zero(cte.totalFreight()).compareTo(zero(quote.totalFreight())) != 0;
    }

    /** Diferença até 1% ou até 1 unidade (kg / R$), o que for maior. */
    static boolean same(BigDecimal left, BigDecimal right) {
        BigDecimal a = zero(left);
        BigDecimal b = zero(right);
        BigDecimal tolerance = a.max(b).multiply(PERCENT).max(ONE);
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    private static String criteria(LegacyCte cte, Candidate candidate) {
        LegacyQuote q = candidate.quote();
        String freight = candidate.cubageException()
                ? "frete CT-e " + money(cte.totalFreight()) + " menor que cotação " + money(q.totalFreight())
                        + " (cotação com cubagem " + plain(q.cubage()) + ")"
                : "frete " + money(cte.totalFreight()) + " x " + money(q.totalFreight());
        return "pagador " + cte.payerCnpj() + "; peso " + plain(cte.weight()) + " x " + plain(q.weight())
                + " kg; NF " + money(cte.invoiceValue()) + " x " + money(q.invoiceValue())
                + (candidate.invoiceDiverges() ? " (NF diferente, frete bate: cotação ajustada ao CT-e)" : "")
                + "; " + freight
                + (candidate.adjustments().isEmpty() ? "" : "; " + candidate.adjustments());
    }

    private static String divergences(LegacyCte cte, LegacyQuote quote) {
        List<String> items = new ArrayList<>();
        if (!equalsText(cte.senderCnpj(), quote.senderCnpj())) {
            items.add("remetente CT-e " + safe(cte.senderCnpj()) + " x cotação " + safe(quote.senderCnpj()));
        }
        if (!equalsText(cte.recipientCnpj(), quote.recipientCnpj())) {
            items.add("destinatário CT-e " + safe(cte.recipientCnpj()) + " x cotação " + safe(quote.recipientCnpj()));
        }
        if (cte.volumes() != quote.volumes()) {
            items.add("volumes CT-e " + cte.volumes() + " x cotação " + quote.volumes());
        }
        return items.isEmpty() ? null : String.join("; ", items);
    }

    private static boolean equalsText(String left, String right) {
        return safe(left).equals(safe(right));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return zero(value).stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return "R$ " + zero(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
