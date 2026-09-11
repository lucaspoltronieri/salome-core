package br.com.salome.core.infrastructure.legacy.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        String contact = "HUB CRM - CT-e " + cte.label();
        String prefix = "[" + LOG_TIME.format(now) + "] [" + USER + "]";
        StringBuilder log = new StringBuilder()
                .append(prefix).append(" [status] [").append(quote.status()).append("] [APROVADA] && ")
                .append(prefix).append(" [statusData] [")
                .append(quote.statusAt() == null ? null : LOG_DATE.format(quote.statusAt()))
                .append("] [").append(LOG_DATE.format(now)).append("] && ")
                .append(prefix).append(" [contatoAprovacao] [").append(quote.approvalContact())
                .append("] [").append(contact).append("] && ");
        String type = properties.approvalType();
        boolean setType = type != null && !type.isBlank();
        String sql = "UPDATE cotacao SET status='APROVADA', statusData=?, statusHora=?, contatoAprovacao=?"
                + (setType ? ", tipoAprovacao=?" : "")
                + ", log=CONCAT(IFNULL(log,''), ?) WHERE idCotacao=? AND status=?";
        Object[] args = setType
                ? new Object[] {Timestamp.valueOf(now), HOUR.format(now), contact, type.trim(), log.toString(),
                        quote.id(), quote.status()}
                : new Object[] {Timestamp.valueOf(now), HOUR.format(now), contact, log.toString(),
                        quote.id(), quote.status()};
        return jdbc.update(sql, args) == 1;
    }
}
