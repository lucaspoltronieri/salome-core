package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.domain.hubcrm.ClientIntegration;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.QuoteIntegration;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                  pdf_status='DISPONIVEL',
                  whatsapp_status=IF(whatsapp_status IN ('ENVIADO','SEM_CONVERSA','ERRO'), whatsapp_status, ?),
                  attempt_count=0, next_attempt_at=NULL, last_error=NULL,
                  last_synced_at=NOW() WHERE legacy_quote_id=?
                """, organizationId, peopleId, dealId, userId, quote.status(), quote.totalFreight(),
                hash, whatsappStatus, quote.id());
    }

    // Card apagado no ArpaSuite: o Hub registra a remoção e não recria. Quem apagou
    // decidiu que o card não deve existir; recriar viraria enxuga-gelo.
    public void markQuoteRemoved(long quoteId) {
        jdbc.update("""
                UPDATE hub_crm_quote SET sync_status='REMOVIDO', deal_id=NULL, attempt_count=0,
                  next_attempt_at=NULL, last_error='Card apagado no ArpaSuite; não recriado',
                  last_synced_at=NOW() WHERE legacy_quote_id=?
                """, quoteId);
    }

    public void markClientRemoved(String cnpj) {
        jdbc.update("""
                UPDATE hub_crm_client SET sync_status='REMOVIDO', deal_id=NULL, attempt_count=0,
                  next_attempt_at=NULL, last_error='Card apagado no ArpaSuite; não recriado',
                  last_synced_at=NOW() WHERE cnpj=?
                """, cnpj);
    }

    /**
     * Cards de cliente (Carteira / Não Pagantes) que ainda não receberam a observação de
     * transportes. Um erro volta a ser tentado depois de um dia.
     */
    public List<ClientCard> clientCardsWithoutTransportNote(int limit) {
        return jdbc.query("""
                SELECT c.legacy_client_id, c.cnpj, c.deal_id FROM hub_crm_client c
                WHERE c.deal_id IS NOT NULL AND c.sync_status <> 'REMOVIDO'
                  AND NOT EXISTS (
                    SELECT 1 FROM hub_crm_event e
                    WHERE e.event_key=CONCAT('client:', c.cnpj, ':transportes')
                      AND (e.status<>'ERRO' OR e.created_at > NOW() - INTERVAL 1 DAY))
                ORDER BY c.legacy_client_id LIMIT ?
                """, (rs, row) -> new ClientCard(rs.getLong("legacy_client_id"), rs.getString("cnpj"),
                rs.getLong("deal_id")), limit);
    }

    public record ClientCard(long legacyClientId, String cnpj, long dealId) {}

    public void markQuoteReview(long quoteId, String error) {
        jdbc.update("""
                UPDATE hub_crm_quote SET sync_status='REVISAO', last_error=? WHERE legacy_quote_id=?
                """, truncate(error), quoteId);
    }

    /** Aviso numa cotação integrada: fica visível na coluna de erro sem tirar o status INTEGRADO. */
    public void markQuoteWarning(long quoteId, String warning) {
        jdbc.update("UPDATE hub_crm_quote SET last_error=? WHERE legacy_quote_id=?", truncate(warning), quoteId);
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

    // ---- Aprovação automática pelo CT-e -------------------------------------------------

    public Set<Long> cteMatchIdsSince(LocalDate since) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT id_conhecimento FROM hub_crm_cte_match WHERE cte_emissao >= ?",
                Long.class, Date.valueOf(since)));
    }

    public Set<Long> quotesApprovedByCte() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT legacy_quote_id FROM hub_crm_cte_match WHERE legacy_quote_id IS NOT NULL",
                Long.class));
    }

    public void recordCteMatch(LegacyCte cte, LegacyQuote quote, String status, String criteria,
            String divergences) {
        jdbc.update("""
                INSERT INTO hub_crm_cte_match
                  (id_conhecimento, cte_numero, cte_serie, cte_chave, cte_emissao, cte_frete, pagador_cnpj,
                   legacy_quote_id, quote_responsavel, quote_status_anterior, quote_frete, status,
                   criterios, divergencias)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status=VALUES(status), criterios=VALUES(criterios),
                  divergencias=VALUES(divergencias)
                """, cte.id(), cte.number(), cte.series(), cte.accessKey(),
                cte.issueDate() == null ? null : Date.valueOf(cte.issueDate()), cte.totalFreight(),
                cte.payerCnpj(),
                "APROVADA_AUTO".equals(status) && quote != null ? quote.id() : null,
                quote == null ? null : quote.responsible(), quote == null ? null : quote.status(),
                quote == null ? null : quote.totalFreight(), status,
                truncate(criteria, 1000), truncate(divergences, 1000));
    }

    public Set<Long> cteMatchIds(Collection<Long> idsConhecimento) {
        if (idsConhecimento == null || idsConhecimento.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(idsConhecimento.size(), "?"));
        return new HashSet<>(jdbc.queryForList(
                "SELECT id_conhecimento FROM hub_crm_cte_match WHERE id_conhecimento IN (" + placeholders + ")",
                Long.class, idsConhecimento.toArray()));
    }

    /**
     * Amarra o CT-e a uma cotação já aprovada, sem reaprovar nada no legado. Diferente do
     * {@link #recordCteMatch}, não sobrescreve linha existente: a tabela tem duas chaves únicas
     * (CT-e e cotação) e um UPDATE cego apagaria a amarração de outro CT-e.
     *
     * @return {@code true} quando a linha foi criada; {@code false} quando o CT-e ou a cotação já
     *         estavam amarrados.
     */
    public boolean recordCteBinding(LegacyCte cte, LegacyQuote quote, String status, String criteria,
            String divergences) {
        try {
            return jdbc.update("""
                    INSERT INTO hub_crm_cte_match
                      (id_conhecimento, cte_numero, cte_serie, cte_chave, cte_emissao, cte_frete, pagador_cnpj,
                       legacy_quote_id, quote_responsavel, quote_status_anterior, quote_frete, status,
                       criterios, divergencias)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id=id
                    """, cte.id(), cte.number(), cte.series(), cte.accessKey(),
                    cte.issueDate() == null ? null : Date.valueOf(cte.issueDate()), cte.totalFreight(),
                    cte.payerCnpj(), quote.id(), quote.responsible(), quote.status(), quote.totalFreight(),
                    status, truncate(criteria, 1000), truncate(divergences, 1000)) == 1;
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // Corrida com a aprovação automática ou com uma ação manual da tela: quem gravou primeiro vale.
            return false;
        }
    }

    /** CT-e amarrado a uma cotação, venha da aprovação automática ou da amarração pós-aprovação. */
    public Optional<CteRef> boundCte(long legacyQuoteId) {
        return jdbc.query("""
                SELECT id_conhecimento, cte_numero, cte_serie, cte_emissao FROM hub_crm_cte_match
                WHERE legacy_quote_id=?
                """, (rs, row) -> new CteRef(rs.getLong("id_conhecimento"), rs.getString("cte_numero"),
                        rs.getString("cte_serie"),
                        rs.getDate("cte_emissao") == null ? null : rs.getDate("cte_emissao").toLocalDate()),
                legacyQuoteId).stream().findFirst();
    }

    /** Identificação curta do CT-e para o acompanhamento e para o card do ArpaSuite. */
    public record CteRef(Long id, String number, String series, LocalDate issueDate) {
        public static CteRef of(LegacyCte cte) {
            return new CteRef(cte.id(), cte.number(), cte.series(), cte.issueDate());
        }

        public String label() {
            return number + (series == null || series.isBlank() ? "" : "/" + series);
        }
    }

    /** Situação da cotação aprovada: quem aprovou, a coleta, o CT-e e o prazo sem CT-e. */
    public void upsertQuoteApproval(long legacyQuoteId, String responsavel, LocalDateTime approvedAt,
            String origem, Long coletaId, String coletaStatus, CteRef cte, String amarracao, String status,
            int days, String detail) {
        jdbc.update("""
                INSERT INTO hub_crm_quote_approval
                  (legacy_quote_id, quote_responsavel, aprovada_em, origem_aprovacao, id_coleta, coleta_status,
                   id_conhecimento, cte_numero, cte_serie, cte_emissao, amarracao, status, dias_desde_aprovacao,
                   detalhe)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE quote_responsavel=VALUES(quote_responsavel),
                  aprovada_em=VALUES(aprovada_em), origem_aprovacao=VALUES(origem_aprovacao),
                  id_coleta=VALUES(id_coleta), coleta_status=VALUES(coleta_status),
                  id_conhecimento=VALUES(id_conhecimento), cte_numero=VALUES(cte_numero),
                  cte_serie=VALUES(cte_serie), cte_emissao=VALUES(cte_emissao), amarracao=VALUES(amarracao),
                  status=VALUES(status), dias_desde_aprovacao=VALUES(dias_desde_aprovacao),
                  detalhe=VALUES(detalhe)
                """, legacyQuoteId, responsavel, approvedAt == null ? null : Timestamp.valueOf(approvedAt),
                origem, coletaId, coletaStatus,
                cte == null ? null : cte.id(), cte == null ? null : cte.number(),
                cte == null ? null : cte.series(),
                cte == null || cte.issueDate() == null ? null : Date.valueOf(cte.issueDate()),
                amarracao, status, days, truncate(detail, 1000));
    }

    /** Situação nova da cotação já acompanhada (ex.: reprovada pela triagem). */
    public void markQuoteApprovalStatus(long legacyQuoteId, String status, String detail) {
        jdbc.update("UPDATE hub_crm_quote_approval SET status=?, detalhe=? WHERE legacy_quote_id=?",
                status, truncate(detail, 1000), legacyQuoteId);
    }

    /** Cotações aprovadas que ainda não têm CT-e amarrado nem foram reprovadas pela triagem. */
    public Set<Long> quotesInReview() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT legacy_quote_id FROM hub_crm_quote_approval WHERE status='REVISAO'", Long.class));
    }

    /**
     * A mudança de status veio do próprio Hub (lote ou aprovação pelo CT-e)? Nesses casos
     * cotação sem card no ArpaSuite não ganha card novo — só se atualiza quem já está lá.
     */
    public boolean changedByHub(long legacyQuoteId) {
        Long count = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM hub_crm_event WHERE event_key IN (?, ?) AND status='PROCESSADO')
                     + (SELECT COUNT(*) FROM hub_crm_cte_match WHERE legacy_quote_id=? AND status='APROVADA_AUTO')
                """, Long.class, "quote:" + legacyQuoteId + ":lote:nao-aprovada",
                "quote:" + legacyQuoteId + ":lote:aprovada", legacyQuoteId);
        return count != null && count > 0;
    }

    /** Ex.: AMBIGUO resolvido manualmente — some da lista de pendências da aba. */
    public void markCteMatchStatus(long idConhecimento, String status) {
        jdbc.update("UPDATE hub_crm_cte_match SET status=? WHERE id_conhecimento=? AND status='AMBIGUO'",
                status, idConhecimento);
    }

    public Optional<String> textCheckpoint(String key) {
        return jdbc.queryForList("SELECT text_value FROM hub_crm_checkpoint WHERE checkpoint_key=?",
                String.class, key).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public void setTextCheckpoint(String key, String value) {
        jdbc.update("""
                INSERT INTO hub_crm_checkpoint (checkpoint_key, text_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE text_value=VALUES(text_value)
                """, key, value);
    }

    /** Texto para a timeline do card quando a cotação foi aprovada pelo CT-e. */
    public Optional<String> cteApprovalNote(long legacyQuoteId) {
        return jdbc.query("""
                SELECT cte_numero, cte_serie, cte_emissao FROM hub_crm_cte_match
                WHERE legacy_quote_id=? AND status='APROVADA_AUTO'
                """, (rs, row) -> {
                    String serie = rs.getString("cte_serie");
                    Date emissao = rs.getDate("cte_emissao");
                    return "CT-e " + rs.getString("cte_numero")
                            + (serie == null || serie.isBlank() ? "" : "/" + serie)
                            + (emissao == null ? "" : " emitido em "
                                    + emissao.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                }, legacyQuoteId).stream().findFirst();
    }

    public List<Map<String, Object>> cteMatches(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT created_at, status, legacy_quote_id, quote_responsavel, quote_status_anterior,
                       cte_numero, cte_serie, cte_emissao, pagador_cnpj, quote_frete, cte_frete,
                       criterios, divergencias, id_conhecimento
                FROM hub_crm_cte_match ORDER BY id DESC LIMIT ?
                """, Math.min(Math.max(limit, 1), 500));
        // legacy_quote_id só é gravado na aprovação automática (é único); no ajuste manual e no
        // empate a cotação está no texto dos critérios, e a tela mostra a coluna preenchida.
        rows.forEach(row -> {
            if (row.get("legacy_quote_id") == null) {
                String quotes = quotesInCriteria((String) row.get("criterios"));
                if (quotes != null) row.put("legacy_quote_id", quotes);
            }
        });
        return rows;
    }

    /**
     * Aba "Aprovação por CT-e": o acompanhamento das cotações aprovadas (com ou sem CT-e) mais as
     * linhas de {@code hub_crm_cte_match} que pedem olho humano (empate, concorrência), que não
     * estão presas a uma cotação acompanhada.
     */
    public List<Map<String, Object>> cteApprovalPanel(int limit) {
        int max = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(jdbc.queryForList("""
                SELECT a.updated_at created_at, a.status, a.legacy_quote_id, a.quote_responsavel,
                       a.origem_aprovacao, a.aprovada_em, a.dias_desde_aprovacao, a.id_coleta,
                       a.coleta_status, a.amarracao, a.cte_numero, a.cte_serie, a.cte_emissao,
                       a.id_conhecimento, m.criterios, m.divergencias, m.pagador_cnpj,
                       m.quote_frete, m.cte_frete, a.detalhe
                FROM hub_crm_quote_approval a
                LEFT JOIN hub_crm_cte_match m ON m.legacy_quote_id=a.legacy_quote_id
                ORDER BY a.aprovada_em DESC, a.legacy_quote_id DESC LIMIT ?
                """, max));
        rows.addAll(jdbc.queryForList("""
                SELECT m.created_at, m.status, m.legacy_quote_id, m.quote_responsavel,
                       NULL origem_aprovacao, NULL aprovada_em, NULL dias_desde_aprovacao, NULL id_coleta,
                       NULL coleta_status, NULL amarracao, m.cte_numero, m.cte_serie, m.cte_emissao,
                       m.id_conhecimento, m.criterios, m.divergencias, m.pagador_cnpj,
                       m.quote_frete, m.cte_frete, NULL detalhe
                FROM hub_crm_cte_match m
                WHERE m.legacy_quote_id IS NULL
                   OR NOT EXISTS (SELECT 1 FROM hub_crm_quote_approval a
                                   WHERE a.legacy_quote_id=m.legacy_quote_id)
                ORDER BY m.id DESC LIMIT ?
                """, max));
        rows.forEach(row -> {
            if (row.get("legacy_quote_id") == null) {
                String quotes = quotesInCriteria((String) row.get("criterios"));
                if (quotes != null) row.put("legacy_quote_id", quotes);
            }
        });
        return rows;
    }

    static String quotesInCriteria(String criteria) {
        if (criteria == null) return null;
        for (String prefix : List.of("Ajuste manual: cotação ", "Cotações empatadas: ")) {
            if (criteria.startsWith(prefix)) return criteria.substring(prefix.length()).trim();
        }
        return null;
    }

    public List<Map<String, Object>> logs(int limit, String entityType, boolean onlyErrors) {
        StringBuilder sql = new StringBuilder("""
                SELECT created_at, processed_at, entity_type, entity_id, event_type, status,
                       response_summary, last_error
                FROM hub_crm_event WHERE 1=1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type=?");
            args.add(entityType.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (onlyErrors) sql.append(" AND status IN ('ERRO','REVISAO')");
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 1000));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> summary() {
        return Map.of(
                "clientes", count("hub_crm_client"),
                "clientesIntegrados", countWhere("hub_crm_client", "sync_status='INTEGRADO'"),
                "cotacoes", count("hub_crm_quote"),
                "cotacoesIntegradas", countWhere("hub_crm_quote", "sync_status='INTEGRADO'"),
                "aprovadasPorCte", countWhere("hub_crm_cte_match", "status='APROVADA_AUTO'"),
                "amarradasPosAprovacao", countWhere("hub_crm_quote_approval", "status='AMARRADA'"),
                "aprovadasSemCte", countWhere("hub_crm_quote_approval", "status='SEM_CTE'"),
                "reprovadasSemCte", countWhere("hub_crm_quote_approval", "status='REPROVADA_SEM_CTE'"),
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
        return truncate(value, 2000);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
