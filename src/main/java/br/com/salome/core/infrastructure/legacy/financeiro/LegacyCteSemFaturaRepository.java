package br.com.salome.core.infrastructure.legacy.financeiro;

import br.com.salome.core.application.financeiro.CteSemFaturaRepository;
import br.com.salome.core.domain.financeiro.CteSemFaturaExportRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "true")
public class LegacyCteSemFaturaRepository implements CteSemFaturaRepository {

    private final JdbcTemplate jdbcTemplate;

    public LegacyCteSemFaturaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CteSemFaturaExportRow> listarEmitidosSemFaturaAte(LocalDate ate) {
        return jdbcTemplate.query("""
                SELECT
                    c.cte numeroCte,
                    c.cteEmissao dataEmissao,
                    remetente.razaoSocial remetente,
                    destinatario.razaoSocial destinatario,
                    c.situacao statusCte,
                    c.valorTotal totalFrete,
                    (
                        SELECT ce.dataEntrega
                        FROM comprovanteentrega ce
                        WHERE ce.idConhecimento = c.idConhecimento
                        ORDER BY ce.idComprovanteEntrega DESC
                        LIMIT 1
                    ) dataEntrega
                FROM conhecimento c
                LEFT JOIN cliente remetente ON remetente.idCliente = c.idClienteEmitente
                LEFT JOIN cliente destinatario ON destinatario.idCliente = c.idClienteDestinatario
                WHERE c.idFatura IS NULL
                  AND c.cte IS NOT NULL
                  AND c.cteEmissao IS NOT NULL
                  AND c.cteEmissao <= ?
                  AND c.cteCancelado IS NULL
                  AND UPPER(COALESCE(c.situacao, '')) NOT IN ('CANCELADA', 'INUTILIZADA', 'DENEGADA', 'DENEGADO')
                ORDER BY c.cteEmissao, c.cte
                """, (rs, rowNum) -> mapRow(rs), ate);
    }

    private CteSemFaturaExportRow mapRow(ResultSet rs) throws SQLException {
        return new CteSemFaturaExportRow(
                integerOrNull(rs, "numeroCte"),
                toLocalDate(rs, "dataEmissao"),
                rs.getString("remetente"),
                rs.getString("destinatario"),
                rs.getString("statusCte"),
                zero(rs.getBigDecimal("totalFrete")),
                toLocalDate(rs, "dataEntrega")
        );
    }

    private LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        return rs.getDate(column) == null ? null : rs.getDate(column).toLocalDate();
    }

    private Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
