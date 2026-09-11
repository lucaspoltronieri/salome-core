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
 *   <li>pagador igual (obrigatório);</li>
 *   <li>CT-e emitido no dia da cotação ou até {@code windowDays} dias depois;</li>
 *   <li>peso, valor da NF e frete iguais (tolerância de arredondamento);</li>
 *   <li>exceção: cotação com cubagem e CT-e com frete menor (cubagem esquecida na
 *       emissão) aprova se peso e NF baterem.</li>
 * </ul>
 * Remetente, destinatário, volumes e natureza não são critério; divergências ficam só
 * registradas. Cotação ABERTA de qualquer responsável é elegível; NÃO APROVADA só das
 * responsáveis que estão no ArpaSuite (Fernanda/Jaci), para reverter a perda.
 */
public final class CteQuoteMatcher {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal PERCENT = new BigDecimal("0.01");

    public enum Outcome { APROVAR, SEM_COTACAO, AMBIGUO }

    public record Match(Outcome outcome, LegacyQuote quote, String criteria, String divergences) {}

    private record Candidate(LegacyQuote quote, BigDecimal freightDiff, boolean cubageException) {}

    private CteQuoteMatcher() {}

    public static Match evaluate(LegacyCte cte, List<LegacyQuote> quotes, int windowDays) {
        List<Candidate> candidates = new ArrayList<>();
        for (LegacyQuote quote : quotes) {
            candidate(cte, quote, windowDays).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) return new Match(Outcome.SEM_COTACAO, null, null, null);

        // Mais próxima da emissão primeiro; depois a de frete mais parecido; depois a mais nova.
        Comparator<Candidate> order = Comparator
                .comparing((Candidate c) -> c.quote().createdDate(), Comparator.reverseOrder())
                .thenComparing(Candidate::freightDiff)
                .thenComparing(c -> c.quote().id(), Comparator.reverseOrder());
        candidates.sort(order);
        Candidate best = candidates.getFirst();
        if (candidates.size() > 1) {
            Candidate second = candidates.get(1);
            if (second.quote().createdDate().equals(best.quote().createdDate())
                    && second.freightDiff().compareTo(best.freightDiff()) == 0) {
                List<String> ids = candidates.stream().map(c -> String.valueOf(c.quote().id())).toList();
                return new Match(Outcome.AMBIGUO, best.quote(), "Cotações empatadas: " + String.join(", ", ids),
                        null);
            }
        }
        return new Match(Outcome.APROVAR, best.quote(), criteria(cte, best), divergences(cte, best.quote()));
    }

    static Optional<Candidate> candidate(LegacyCte cte, LegacyQuote quote, int windowDays) {
        if (!eligibleStatus(quote)) return Optional.empty();
        if (quote.createdDate() == null || cte.issueDate() == null) return Optional.empty();
        long days = ChronoUnit.DAYS.between(quote.createdDate(), cte.issueDate());
        if (days < 0 || days > windowDays) return Optional.empty();
        if (blank(cte.payerCnpj()) || !cte.payerCnpj().equals(quote.payerCnpj())) return Optional.empty();
        if (!same(cte.weight(), quote.weight())) return Optional.empty();
        if (!same(cte.invoiceValue(), quote.invoiceValue())) return Optional.empty();
        BigDecimal cteFreight = zero(cte.totalFreight());
        BigDecimal quoteFreight = zero(quote.totalFreight());
        boolean freightOk = same(cteFreight, quoteFreight);
        boolean cubageException = !freightOk && positive(quote.cubage())
                && cteFreight.signum() > 0 && cteFreight.compareTo(quoteFreight) < 0;
        if (!freightOk && !cubageException) return Optional.empty();
        return Optional.of(new Candidate(quote, cteFreight.subtract(quoteFreight).abs(), cubageException));
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
                + " kg; NF " + money(cte.invoiceValue()) + " x " + money(q.invoiceValue()) + "; " + freight;
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
