package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.domain.hubcrm.ClientIntegration;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.QuoteIntegration;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmStore {
    private final JdbcTemplate jdbc;

    public HubCrmStore(@Qualifier("hubCrmJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void discoverClient(LegacyCrmClient client, String normalizedName, String hash) {
        jdbc.update("""
                INSERT INTO hub_crm_client
                  (legacy_client_id, cnpj, razao_social, razao_social_normalizada,
                   first_cte_without_freight, snapshot_hash, sync_status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDENTE')
                ON DUPLICATE KEY UPDATE
                  razao_social=VALUES(razao_social),
                  razao_social_normalizada=VALUES(razao_social_normalizada),
                  first_cte_without_freight=VALUES(first_cte_without_freight),
                  sync_status=IF(sync_status='INTEGRADO' AND snapshot_hash<>VALUES(snapshot_hash),
                                 'ATUALIZAR', sync_status),
                  snapshot_hash=IF(sync_status IN ('INTEGRADO','ATUALIZAR') AND snapshot_hash<>VALUES(snapshot_hash),
                                   VALUES(snapshot_hash), snapshot_hash)
                """, client.legacyClientId(), client.cnpj(), client.legalName(), normalizedName,
                Date.valueOf(client.firstCteWithoutFreight()), hash);
    }

    public Optional<ClientIntegration> findClient(String cnpj) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT legacy_client_id, cnpj, organization_id, people_id, deal_id,
                           assigned_user_id, first_cte_without_freight, snapshot_hash, sync_status
                    FROM hub_crm_client WHERE cnpj=?
                    """, (rs, row) -> new ClientIntegration(
                    rs.getLong("legacy_client_id"), rs.getString("cnpj"),
                    nullableLong(rs, "organization_id"), nullableLong(rs, "people_id"),
                    nullableLong(rs, "deal_id"), nullableLong(rs, "assigned_user_id"),
                    rs.getDate("first_cte_without_freight").toLocalDate(),
                    rs.getString("snapshot_hash"), rs.getString("sync_status")), cnpj));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public void markClientIntegrated(String cnpj, long organizationId, Long peopleId,
            long dealId, long userId, String hash) {
        jdbc.update("""
                UPDATE hub_crm_client SET organization_id=?, people_id=?, deal_id=?,
                  assigned_user_id=?, snapshot_hash=?, sync_status='INTEGRADO', attempt_count=0,
                  next_attempt_at=NULL, last_error=NULL, last_synced_at=NOW() WHERE cnpj=?
                """, organizationId, peopleId, dealId, userId, hash, cnpj);
    }

    public void markClientError(String cnpj, Exception error) {
        jdbc.update("""
                UPDATE hub_crm_client SET sync_status='ERRO', attempt_count=attempt_count+1,
                  next_attempt_at=TIMESTAMPADD(MINUTE, LEAST(POW(2, attempt_count), 60), NOW()),
                  last_error=? WHERE cnpj=?
                """, truncate(error.getMessage()), cnpj);
    }

    public boolean canRetryClient(String cnpj) {
        Boolean result = jdbc.queryForObject("""
                SELECT next_attempt_at IS NULL OR next_attempt_at<=NOW()
                FROM hub_crm_client WHERE cnpj=?
                """, Boolean.class, cnpj);
        return Boolean.TRUE.equals(result);
    }

    @Transactional("hubCrmTransactionManager")
    public long nextRoundRobinUser(long fernandaUserId, long jaciUserId) {
        Long cursor = jdbc.queryForObject("""
                SELECT numeric_value FROM hub_crm_checkpoint
                WHERE checkpoint_key='round_robin_cursor' FOR UPDATE
                """, Long.class);
        long current = cursor == null ? 0 : cursor;
        jdbc.update("""
                UPDATE hub_crm_checkpoint SET numeric_value=?
                WHERE checkpoint_key='round_robin_cursor'
                """, current + 1);
        return current % 2 == 0 ? fernandaUserId : jaciUserId;
    }

    public long checkpoint(String key, long defaultValue) {
        try {
            Long value = jdbc.queryForObject(
                    "SELECT numeric_value FROM hub_crm_checkpoint WHERE checkpoint_key=?", Long.class, key);
            return value == null ? defaultValue : value;
        } catch (EmptyResultDataAccessException ignored) {
            return defaultValue;
        }
    }

    public void setCheckpoint(String key, long value) {
        jdbc.update("""
                INSERT INTO hub_crm_checkpoint (checkpoint_key, numeric_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE numeric_value=VALUES(numeric_value)
                """, key, value);
    }

    public void discoverQuote(LegacyQuote quote, String hash) {
        jdbc.update("""
                INSERT INTO hub_crm_quote
                  (legacy_quote_id, payer_cnpj, legacy_responsible, legacy_status,
                   total_freight, snapshot_hash, sync_status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDENTE')
                ON DUPLICATE KEY UPDATE
                  payer_cnpj=VALUES(payer_cnpj), legacy_responsible=VALUES(legacy_responsible),
                  legacy_status=VALUES(legacy_status), total_freight=VALUES(total_freight),
                  sync_status=IF(snapshot_hash<>VALUES(snapshot_hash), 'ATUALIZAR', sync_status),
                  snapshot_hash=VALUES(snapshot_hash)
                """, quote.id(), quote.payerCnpj(), quote.responsible(), quote.status(),
                quote.totalFreight(), hash);
    }

    public Optional<QuoteIntegration> findQuote(long legacyQuoteId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT legacy_quote_id, payer_cnpj, legacy_status, deal_id, organization_id,
                           people_id, assigned_user_id, total_freight, snapshot_hash, sync_status,
                           whatsapp_status
                    FROM hub_crm_quote WHERE legacy_quote_id=?
                    """, (rs, row) -> new QuoteIntegration(
                    rs.getLong("legacy_quote_id"), rs.getString("payer_cnpj"),
                    rs.getString("legacy_status"), nullableLong(rs, "deal_id"),
                    nullableLong(rs, "organization_id"), nullableLong(rs, "people_id"),
                    nullableLong(rs, "assigned_user_id"), rs.getBigDecimal("total_freight"),
                    rs.getString("snapshot_hash"), rs.getString("sync_status"),
                    rs.getString("whatsapp_status")), legacyQuoteId));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<Long> trackedQuoteIds() {
        return jdbc.queryForList("""
                SELECT legacy_quote_id FROM hub_crm_quote
                WHERE sync_status IN ('PENDENTE','ATUALIZAR','INTEGRADO','ERRO','REVISAO')
                """, Long.class);
    }

    public void bindQuote(long legacyQuoteId, long organizationId, long peopleId,
            long dealId, long userId) {
        jdbc.update("""
                UPDATE hub_crm_quote SET organization_id=?, people_id=?, deal_id=?, assigned_user_id=?,
                  last_synced_at=NOW() WHERE legacy_quote_id=?
                """, organizationId, peopleId, dealId, userId, legacyQuoteId);
    }

    public boolean dealBoundToOtherQuote(long dealId, long legacyQuoteId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM hub_crm_quote
                WHERE deal_id=? AND legacy_quote_id<>?
                """, Long.class, dealId, legacyQuoteId);
        return count != null && count > 0;
    }

    public void markQuoteIntegrated(LegacyQuote quote, long organizationId, long peopleId,
            long dealId, long userId, String hash, String whatsappStatus) {
        jdbc.update("""
                UPDATE hub_crm_quote SET organization_id=?, people_id=?, deal_id=?, assigned_user_id=?,
                  legacy_status=?, total_freight=?, snapshot_hash=?, sync_status='INTEGRADO',
                  pdf_status='DISPONIVEL', whatsapp_status=?, attempt_count=0, next_attempt_at=NULL, last_error=NULL,
                  last_synced_at=NOW() WHERE legacy_quote_id=?
                """, organizationId, peopleId, dealId, userId, quote.status(), quote.totalFreight(),
                hash, whatsappStatus, quote.id());
    }

    public void markQuoteReview(long quoteId, String error) {
        jdbc.update("""
                UPDATE hub_crm_quote SET sync_status='REVISAO', last_error=? WHERE legacy_quote_id=?
                """, truncate(error), quoteId);
    }

    public void markWhatsapp(long quoteId, String status, String error) {
        jdbc.update("UPDATE hub_crm_quote SET whatsapp_status=?, last_error=? WHERE legacy_quote_id=?",
                status, truncate(error), quoteId);
    }

    public void markQuoteError(long quoteId, Exception error) {
        jdbc.update("""
                UPDATE hub_crm_quote SET sync_status='ERRO', attempt_count=attempt_count+1,
                  next_attempt_at=TIMESTAMPADD(MINUTE, LEAST(POW(2, attempt_count), 60), NOW()),
                  last_error=? WHERE legacy_quote_id=?
                """, truncate(error.getMessage()), quoteId);
    }

    public boolean canRetryQuote(long quoteId) {
        Boolean result = jdbc.queryForObject("""
                SELECT next_attempt_at IS NULL OR next_attempt_at<=NOW()
                FROM hub_crm_quote WHERE legacy_quote_id=?
                """, Boolean.class, quoteId);
        return Boolean.TRUE.equals(result);
    }

    public void reprocess(String entityType, long legacyId) {
        if ("CLIENTE".equalsIgnoreCase(entityType)) {
            jdbc.update("""
                    UPDATE hub_crm_client SET sync_status='ATUALIZAR', next_attempt_at=NULL,
                      last_error=NULL WHERE legacy_client_id=?
                    """, legacyId);
        } else if ("COTACAO".equalsIgnoreCase(entityType) || "COTAÇÃO".equalsIgnoreCase(entityType)) {
            jdbc.update("""
                    UPDATE hub_crm_quote SET sync_status='ATUALIZAR', next_attempt_at=NULL,
                      last_error=NULL WHERE legacy_quote_id=?
                    """, legacyId);
        } else {
            throw new IllegalArgumentException("Tipo deve ser CLIENTE ou COTACAO");
        }
    }

    public void recordEvent(String key, String entityType, long entityId, String eventType,
            String status, String response, String error) {
        jdbc.update("""
                INSERT INTO hub_crm_event
                  (event_key, entity_type, entity_id, event_type, status, response_summary,
                   last_error, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status=VALUES(status), response_summary=VALUES(response_summary),
                  last_error=VALUES(last_error), processed_at=VALUES(processed_at)
                """, key, entityType, entityId, eventType, status, truncate(response),
                truncate(error), "PROCESSADO".equals(status) ? Timestamp.valueOf(LocalDateTime.now()) : null);
    }

    public boolean eventProcessed(String key) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hub_crm_event WHERE event_key=? AND status='PROCESSADO'",
                Long.class, key);
        return count != null && count > 0;
    }

    public boolean hasProcessedQuoteContentEvent(long quoteId) {
        return hasProcessedEventPrefix("quote:" + quoteId + ":content:");
    }

    public boolean hasProcessedLegacyQuoteSnapshotEvent(long quoteId) {
        return hasProcessedEventPrefix("quote:" + quoteId + ":snapshot:");
    }

    private boolean hasProcessedEventPrefix(String prefix) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM hub_crm_event
                WHERE event_key LIKE ? AND status='PROCESSADO'
                """, Long.class, prefix + "%");
        return count != null && count > 0;
    }

    public Map<String, Object> summary() {
        return Map.of(
                "clientes", count("hub_crm_client"),
                "clientesIntegrados", countWhere("hub_crm_client", "sync_status='INTEGRADO'"),
                "cotacoes", count("hub_crm_quote"),
                "cotacoesIntegradas", countWhere("hub_crm_quote", "sync_status='INTEGRADO'"),
                "erros", countWhere("hub_crm_event", "status IN ('ERRO','REVISAO')"));
    }

    public List<Map<String, Object>> clients(int limit) {
        return jdbc.queryForList("""
                SELECT legacy_client_id, cnpj, razao_social, organization_id, people_id, deal_id,
                       assigned_user_id, first_cte_without_freight, sync_status, last_error, updated_at
                FROM hub_crm_client ORDER BY updated_at DESC LIMIT ?
                """, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> quotes(int limit) {
        return jdbc.queryForList("""
                SELECT legacy_quote_id, payer_cnpj, legacy_responsible, legacy_status, total_freight,
                       deal_id, assigned_user_id, pdf_status, whatsapp_status, sync_status,
                       last_error, updated_at
                FROM hub_crm_quote ORDER BY updated_at DESC LIMIT ?
                """, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> events(int limit) {
        return jdbc.queryForList("""
                SELECT id, entity_type, entity_id, event_type, status, attempt_count,
                       last_error, created_at, processed_at
                FROM hub_crm_event ORDER BY id DESC LIMIT ?
                """, Math.min(Math.max(limit, 1), 500));
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private long countWhere(String table, String where) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return value == null ? 0 : value;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
