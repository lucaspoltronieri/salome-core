package br.com.salome.core.infrastructure.legacy.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmLegacyRepository;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class LegacyHubCrmRepository implements HubCrmLegacyRepository {
    private static final String CLIENT_SQL = """
            SELECT cl.idCliente, cl.razaoSocial,
                   REGEXP_REPLACE(COALESCE(cl.cnpj_cpf,''),'[^0-9]','') cnpj,
                   ci.descricao cidade, es.uf estado, cl.email, cl.telefone,
                   MIN(k.cteEmissao) primeiroCte, cn.descricao segmento,
                   GROUP_CONCAT(DISTINCT CONCAT_WS(' | ', NULLIF(TRIM(cc.nome),''),
                     NULLIF(TRIM(cs.descricao),''), NULLIF(TRIM(cc.email),''),
                     NULLIF(TRIM(cc.telefone),'')) ORDER BY cc.idClienteContato SEPARATOR ' || ') contatos
            FROM cliente cl
            JOIN cidade ci ON ci.idCidade=cl.idCidade
            JOIN estado es ON es.idEstado=ci.idEstado
            JOIN conhecimento k ON k.idClienteDestinatario=cl.idCliente
              AND k.cte IS NOT NULL AND k.cteEmissao >= '1000-01-01'
              AND (k.cteCancelado IS NULL OR k.cteCancelado=0
                   OR UPPER(CAST(k.cteCancelado AS CHAR)) NOT IN ('S','SIM','1'))
              AND NOT (UPPER(COALESCE(k.tipoPagamento,'')) LIKE '%DESTINAT%'
                       AND UPPER(COALESCE(k.tipoPagamento,'')) LIKE '%FOB%')
            LEFT JOIN cnae cn ON cn.codigo=cl.cnae
            LEFT JOIN clientecontato cc ON cc.idCliente=cl.idCliente
              AND UPPER(TRIM(COALESCE(cc.nome,''))) <> 'ERICK'
              AND LOWER(TRIM(COALESCE(cc.email,''))) NOT IN
                  ('erick@salome.com.br','erickcardozo@salome.com.br','ti@ti.com.br')
            LEFT JOIN clientesetor cs ON cs.idClienteSetor=cc.idClienteSetor
            WHERE cl.idCliente >= ?
              AND LENGTH(REGEXP_REPLACE(COALESCE(cl.cnpj_cpf,''),'[^0-9]',''))=14
              AND UPPER(es.uf)='SP'
              AND NOT EXISTS (
                SELECT 1 FROM conhecimento p
                WHERE p.idClienteDestinatario=cl.idCliente AND p.cte IS NOT NULL
                  AND (p.cteCancelado IS NULL OR p.cteCancelado=0
                       OR UPPER(CAST(p.cteCancelado AS CHAR)) NOT IN ('S','SIM','1'))
                  AND UPPER(COALESCE(p.tipoPagamento,'')) LIKE '%DESTINAT%'
                  AND UPPER(COALESCE(p.tipoPagamento,'')) LIKE '%FOB%')
              AND UPPER(COALESCE(cn.descricao,'')) NOT LIKE '%TRANSPORT%'
              AND NOT (UPPER(COALESCE(cl.cnaeSegmentoDetalhes,'')) LIKE '%MEI%'
                       AND UPPER(COALESCE(cl.cnaeSegmentoDetalhes,'')) LIKE '%SERVI%')
            GROUP BY cl.idCliente, cl.razaoSocial, cl.cnpj_cpf, ci.descricao, es.uf,
                     cl.email, cl.telefone, cn.descricao
            ORDER BY cl.idCliente
            """;

    private static final String QUOTE_SELECT = """
            SELECT q.*, remCi.descricao remetenteCidade, destCi.descricao destinatarioCidade,
                   nat.descricao naturezaCarga
            FROM cotacao q
            LEFT JOIN cidade remCi ON remCi.idCidade=q.remetenteIdCidade
            LEFT JOIN cidade destCi ON destCi.idCidade=q.destinatarioIdCidade
            LEFT JOIN naturezacargacliente ncc ON ncc.idNaturezaCargaCliente=q.idNaturezaCargaCliente
            LEFT JOIN naturezacarga nat ON nat.idNaturezaCarga=COALESCE(q.idNaturezaCarga,ncc.idNaturezaCarga)
            """;

    private final JdbcTemplate jdbc;

    public LegacyHubCrmRepository(@Qualifier("legacyJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LegacyCrmClient> findEligibleClients(long cutoffClientId) {
        return jdbc.query(CLIENT_SQL, (rs, row) -> mapClient(rs), cutoffClientId);
    }

    @Override
    public List<LegacyQuote> findQuotesAfter(long quoteId) {
        return jdbc.query(QUOTE_SELECT + """
                WHERE q.idCotacao > ?
                  AND UPPER(TRIM(q.responsavel)) IN ('FERNANDA','JACI','JACI QUEIROZ','QUEIROZ')
                ORDER BY q.idCotacao
                """, (rs, row) -> mapQuote(rs), quoteId);
    }

    @Override
    public List<LegacyQuote> findQuotesByIds(Collection<Long> quoteIds) {
        if (quoteIds == null || quoteIds.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>(quoteIds);
        List<LegacyQuote> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> batch = ids.subList(start, Math.min(start + 500, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            result.addAll(jdbc.query(QUOTE_SELECT + " WHERE q.idCotacao IN (" + placeholders + ")",
                    (rs, row) -> mapQuote(rs), batch.toArray()));
        }
        return result;
    }

    private LegacyCrmClient mapClient(ResultSet rs) throws SQLException {
        String[] contact = splitContact(rs.getString("contatos"));
        return new LegacyCrmClient(
                rs.getLong("idCliente"), rs.getString("razaoSocial"), rs.getString("cnpj"),
                rs.getString("cidade"), rs.getString("estado"), rs.getString("email"),
                rs.getString("telefone"), rs.getString("segmento"), contact[0], contact[1],
                contact[2], contact[3], rs.getDate("primeiroCte").toLocalDate());
    }

    private LegacyQuote mapQuote(ResultSet rs) throws SQLException {
        String payment = rs.getString("tipoPagamento");
        boolean fob = normalized(payment).contains("DESTINAT") && normalized(payment).contains("FOB");
        String payerCnpj = HubCrmNormalization.digits(
                rs.getString(fob ? "destinatarioCnpj" : "remetenteCnpj"));
        String payerName = rs.getString(fob ? "destinatarioRazaoSocial" : "remetenteRazaoSocial");
        String payerPhone = rs.getString(fob ? "destinatarioTelefone" : "remetenteTelefone");
        String payerEmail = rs.getString(fob ? "destinatarioEmail" : "remetenteEmail");
        return new LegacyQuote(
                rs.getLong("idCotacao"), localDate(rs, "data"), rs.getString("hora"),
                rs.getString("responsavel"), rs.getString("status"), statusAt(rs), payment,
                HubCrmNormalization.digits(rs.getString("remetenteCnpj")),
                rs.getString("remetenteRazaoSocial"), rs.getString("remetenteCidade"),
                HubCrmNormalization.digits(rs.getString("destinatarioCnpj")),
                rs.getString("destinatarioRazaoSocial"), rs.getString("destinatarioCidade"),
                payerCnpj, payerName, payerPhone, payerEmail, rs.getString("naturezaCarga"),
                rs.getInt("quantidadeVolumes"), rs.getBigDecimal("peso"), rs.getBigDecimal("valorNf"),
                rs.getBigDecimal("cubagem"), rs.getBigDecimal("fretePesoValor"),
                rs.getBigDecimal("freteValor"), rs.getBigDecimal("pedagioValor"),
                rs.getBigDecimal("coletaValor"), rs.getBigDecimal("entregaValor"),
                rs.getBigDecimal("despachoValor"), rs.getBigDecimal("grisValor"),
                rs.getBigDecimal("redespachoValor"), rs.getBigDecimal("icmsValor"),
                rs.getBigDecimal("descontoValor"), rs.getBigDecimal("acrescimoValor"),
                rs.getBigDecimal("totalFrete"), rs.getString("contatoAprovacao"), lossReasons(rs));
    }

    private Map<LossReason, String> lossReasons(ResultSet rs) throws SQLException {
        Map<LossReason, String> reasons = new EnumMap<>(LossReason.class);
        addReason(reasons, rs, LossReason.HIGH_PRICE, "naoAprovacaoPreco", "naoAprovacaoPrecoDescricao");
        addReason(reasons, rs, LossReason.BAD_DEADLINE_WINDOW, "naoAprovacaoPrazo", "naoAprovacaoPrazoDescricao");
        addReason(reasons, rs, LossReason.SPECIAL_DEDICATED_CARGO, "naoAprovacaoCargaEspecial", "naoAprovacaoCargaEspecialDescricao");
        addReason(reasons, rs, LossReason.OUTSIDE_IDEAL_PROFILE, "naoAprovacaoForaPerfil", "naoAprovacaoForaPerfilDescricao");
        addReason(reasons, rs, LossReason.NO_CREDIT, "naoAprovacaoSemCredito", "naoAprovacaoSemCreditoDescricao");
        addReason(reasons, rs, LossReason.NO_INSURANCE, "naoAprovacaoSemSeguro", "naoAprovacaoSemSeguroDescricao");
        addReason(reasons, rs, LossReason.FREIGHT_REGRET, "naoAprovacaoArrependimentoFrete", "naoAprovacaoArrependimentoFreteDescricao");
        addReason(reasons, rs, LossReason.PICKUP_PROBLEM, "naoAprovacaoProblemaColeta", "naoAprovacaoProblemaColetaDescricao");
        addReason(reasons, rs, LossReason.DEFAULT, "naoAprovacaoInadimplencia", "naoAprovacaoInadimplenciaDescricao");
        addReason(reasons, rs, LossReason.COMPETITOR, "naoAprovacaoConcorrente", "naoAprovacaoConcorrenteDescricao");
        return Map.copyOf(reasons);
    }

    private void addReason(Map<LossReason, String> target, ResultSet rs, LossReason reason,
            String flagColumn, String detailColumn) throws SQLException {
        if ("SIM".equals(normalized(rs.getString(flagColumn)))) {
            String detail = rs.getString(detailColumn);
            target.put(reason, detail == null ? "" : detail);
        }
    }

    private LocalDateTime statusAt(ResultSet rs) throws SQLException {
        LocalDate date = localDate(rs, "statusData");
        if (date == null) return null;
        String text = rs.getString("statusHora");
        try {
            return date.atTime(text == null || text.isBlank() ? LocalTime.MIDNIGHT : LocalTime.parse(text.trim()));
        } catch (RuntimeException ignored) {
            return date.atStartOfDay();
        }
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private String[] splitContact(String value) {
        String[] empty = {"", "", "", ""};
        if (value == null || value.isBlank()) return empty;
        String[] parts = value.split(" \\| ", -1);
        for (int index = 0; index < Math.min(parts.length, 4); index++) empty[index] = parts[index].trim();
        return empty;
    }

    private String normalized(String value) {
        return HubCrmNormalization.normalizedText(value);
    }
}
