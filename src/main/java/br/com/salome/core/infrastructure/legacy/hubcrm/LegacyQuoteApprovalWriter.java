package br.com.salome.core.infrastructure.legacy.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Única escrita do Hub no legado: aprova a cotação com o usuário {@code crm_api}, gravando
 * os mesmos campos da tela {@code CotacaoAprovacao} e uma linha no {@code log} no formato
 * do {@code CotacaoData.alterar}. A trava {@code AND status=?} impede sobrescrever uma
 * cotação que alguém alterou entre a leitura e a gravação.
 */
@Repository
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class LegacyQuoteApprovalWriter {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final String USER = "crm_api";

    private final JdbcTemplate jdbc;
    private final HubCrmAutoApprovalProperties properties;

    public LegacyQuoteApprovalWriter(@Qualifier("legacyWriteJdbcTemplate") JdbcTemplate jdbc,
            HubCrmAutoApprovalProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** @return {@code true} quando a cotação foi aprovada; {@code false} se o status mudou no meio. */
    public boolean approve(LegacyQuote quote, LegacyCte cte, LocalDateTime now) {
        return approve(quote, cte, now, false);
    }

    /**
     * Aprova a cotação pelo CT-e. Com {@code syncValues}, grava também peso, NF e a composição do
     * frete do CT-e na cotação (regra de aprovação com pouca diferença), cada campo alterado com a
     * sua linha no {@code log}. Cotação já APROVADA (aprovada à mão e reenviada pelo usuário da API)
     * mantém a data da aprovação, para o ganho no ArpaSuite não sair de novo.
     */
    public boolean approve(LegacyQuote quote, LegacyCte cte, LocalDateTime now, boolean syncValues) {
        String contact = "HUB CRM - CT-e " + cte.label();
        String prefix = "[" + LOG_TIME.format(now) + "] [" + USER + "]";
        boolean alreadyApproved = "APROVADA".equals(HubCrmNormalization.normalizedText(quote.status()));
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        if (!alreadyApproved) {
            sets.add("status='APROVADA'");
            sets.add("statusData=?");
            args.add(Timestamp.valueOf(now));
            sets.add("statusHora=?");
            args.add(HOUR.format(now));
            log.append(prefix).append(" [status] [").append(quote.status()).append("] [APROVADA] && ")
                    .append(prefix).append(" [statusData] [")
                    .append(quote.statusAt() == null ? null : LOG_DATE.format(quote.statusAt()))
                    .append("] [").append(LOG_DATE.format(now)).append("] && ");
        }
        sets.add("contatoAprovacao=?");
        args.add(contact);
        log.append(prefix).append(" [contatoAprovacao] [").append(quote.approvalContact())
                .append("] [").append(contact).append("] && ");
        String type = properties.approvalType();
        if (type != null && !type.isBlank()) {
            sets.add("tipoAprovacao=?");
            args.add(type.trim());
        }
        if (syncValues) {
            for (ValueChange change : valueChanges(quote, cte)) {
                sets.add(change.column() + "=?");
                args.add(change.value());
                log.append(prefix).append(" [").append(change.column().toUpperCase(Locale.ROOT)).append("] [")
                        .append(logNumber(change.previous())).append("] [").append(logNumber(change.value()))
                        .append("] && ");
            }
        }
        sets.add("log=CONCAT(IFNULL(log,''), ?)");
        args.add(log.toString());
        args.add(quote.id());
        args.add(quote.status());
        return jdbc.update("UPDATE cotacao SET " + String.join(", ", sets) + " WHERE idCotacao=? AND status=?",
                args.toArray()) == 1;
    }

    record ValueChange(String column, BigDecimal previous, BigDecimal value) {}

    /** Campos da cotação que ficam iguais ao CT-e; só entram os que mudam. */
    static List<ValueChange> valueChanges(LegacyQuote quote, LegacyCte cte) {
        List<ValueChange> changes = new ArrayList<>();
        change(changes, "peso", quote.weight(), cte.weight());
        change(changes, "valorNf", quote.invoiceValue(), cte.invoiceValue());
        LegacyCte.Charges c = cte.charges();
        if (c != null) {
            change(changes, "fretePesoValor", quote.freightWeight(), c.freightWeight());
            change(changes, "freteValor", quote.freightValue(), c.freightValue());
            change(changes, "pedagioValor", quote.toll(), c.toll());
            change(changes, "coletaValor", quote.pickup(), c.pickup());
            change(changes, "entregaValor", quote.delivery(), c.delivery());
            change(changes, "despachoValor", quote.dispatch(), c.dispatch());
            change(changes, "grisValor", quote.gris(), c.gris());
            change(changes, "redespachoValor", quote.redelivery(), c.redelivery());
            change(changes, "icmsValor", quote.icms(), c.icms());
            change(changes, "descontoValor", quote.discount(), c.discount());
            change(changes, "acrescimoValor", quote.addition(), c.addition());
        }
        change(changes, "totalFrete", quote.totalFreight(), cte.totalFreight());
        return changes;
    }

    private static void change(List<ValueChange> changes, String column, BigDecimal previous, BigDecimal value) {
        if (value == null) return;
        BigDecimal before = previous == null ? BigDecimal.ZERO : previous;
        if (before.compareTo(value) != 0) changes.add(new ValueChange(column, previous, value));
    }

    /** Mesmo formato dos números no log do legado (Double.toString: 104.8, 2507.0). */
    private static String logNumber(BigDecimal value) {
        return value == null ? "null" : String.valueOf(value.doubleValue());
    }

    /**
     * Marca a cotação como NÃO APROVADA com o motivo Preço (lote de limpeza das cotações
     * abertas sem CT-e). Mesmos campos da tela de não aprovação e mesma trava de status.
     */
    public boolean rejectForPrice(LegacyQuote quote, String description, LocalDateTime now) {
        String prefix = "[" + LOG_TIME.format(now) + "] [" + USER + "]";
        String log = prefix + " [status] [" + quote.status() + "] [NÃO APROVADA] && "
                + prefix + " [statusData] [" + (quote.statusAt() == null ? null : LOG_DATE.format(quote.statusAt()))
                + "] [" + LOG_DATE.format(now) + "] && "
                + prefix + " [naoAprovacaoPreco] [null] [Sim] && "
                + prefix + " [naoAprovacaoPrecoDescricao] [null] [" + description + "] && ";
        return jdbc.update("""
                UPDATE cotacao SET status='NÃO APROVADA', statusData=?, statusHora=?,
                  naoAprovacaoPreco='Sim', naoAprovacaoPrecoDescricao=?, log=CONCAT(IFNULL(log,''), ?)
                WHERE idCotacao=? AND status=?
                """, Timestamp.valueOf(now), HOUR.format(now), description, log, quote.id(), quote.status()) == 1;
    }
}
