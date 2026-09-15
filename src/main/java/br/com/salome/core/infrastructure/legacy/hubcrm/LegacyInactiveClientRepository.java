package br.com.salome.core.infrastructure.legacy.hubcrm;

import br.com.salome.core.application.hubcrm.InactiveClientRepository;
import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Mesma regra da planilha de clientes inativos: tomador = destinatário quando o pagamento é
 * "Destinatário (FOB)", senão o emitente; CT-e autorizado, não cancelado, sem cortesia e com
 * situação Finalizada/Em viagem/Armazém. Assim o total do PDF bate com o frete da planilha.
 * O relatório de "recebidos" usa os mesmos filtros, com o cliente no destinatário sem ser o tomador.
 */
@Repository
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class LegacyInactiveClientRepository implements InactiveClientRepository {
    static final String RECEIVED_PERIOD = "recebidos";
    private static final LocalDate RECEIVED_SINCE = LocalDate.of(2020, 1, 1);

    private static final String CLIENT_SQL = """
            SELECT cl.razaoSocial, cl.fantasia, REGEXP_REPLACE(COALESCE(cl.cnpj_cpf,''),'[^0-9]','') cnpj,
                   ci.descricao cidade, es.uf
            FROM cliente cl
            LEFT JOIN cidade ci ON ci.idCidade=cl.idCidade
            LEFT JOIN estado es ON es.idEstado=ci.idEstado
            WHERE cl.idCliente=?
            """;

    private static final String CTE_SELECT = """
            SELECT c.cte, c.cteEmissao, c.tipoPagamento, c.valorTotal,
                   em.razaoSocial remetente, emCi.descricao remetenteCidade,
                   REGEXP_REPLACE(COALESCE(em.cnpj_cpf,''),'[^0-9]','') remetenteCnpj,
                   de.razaoSocial destinatario, deCi.descricao destinatarioCidade,
                   REGEXP_REPLACE(COALESCE(de.cnpj_cpf,''),'[^0-9]','') destinatarioCnpj,
                   (SELECT GROUP_CONCAT(nf.numero ORDER BY nf.numero SEPARATOR ', ')
                      FROM conhecimentonotasfiscais nf WHERE nf.idConhecimento=c.idConhecimento) notas,
                   (SELECT SUM(IFNULL(nf.pesoNf,0)) FROM conhecimentonotasfiscais nf
                     WHERE nf.idConhecimento=c.idConhecimento) peso,
                   (SELECT SUM(IFNULL(nf.valorNF,0)) FROM conhecimentonotasfiscais nf
                     WHERE nf.idConhecimento=c.idConhecimento) valorNf,
                   (SELECT SUM(IFNULL(nf.quantidadeVolumes,0)) FROM conhecimentonotasfiscais nf
                     WHERE nf.idConhecimento=c.idConhecimento) volumes
            FROM conhecimento c
            LEFT JOIN cliente em ON em.idCliente=c.idClienteEmitente
            LEFT JOIN cidade emCi ON emCi.idCidade=em.idCidade
            LEFT JOIN cliente de ON de.idCliente=c.idClienteDestinatario
            LEFT JOIN cidade deCi ON deCi.idCidade=de.idCidade
            WHERE c.cteEmissao >= ? AND c.cteEmissao < ?
              AND COALESCE(c.cte,0)>0
              AND c.cteCancelado IS NULL
              AND UPPER(TRIM(COALESCE(c.situacao,''))) IN ('FINALIZADA','EM VIAGEM','ARMAZÉM')
              AND UPPER(TRIM(COALESCE(c.tipoFrete,'')))<>'CORTESIA'
            """;

    private static final String CTE_SQL = CTE_SELECT + """
              AND IF(UPPER(COALESCE(c.tipoPagamento,'')) LIKE 'DESTINAT%'
                     AND UPPER(COALESCE(c.tipoPagamento,'')) LIKE '%FOB%',
                     c.idClienteDestinatario, c.idClienteEmitente)=?
            ORDER BY c.cteEmissao, c.cte
            """;

    // Destinatário que não paga: mesma condição do cadastro que vira card na Carteira.
    private static final String RECEIVED_SQL = CTE_SELECT + """
              AND c.idClienteDestinatario=?
              AND NOT (UPPER(COALESCE(c.tipoPagamento,'')) LIKE '%DESTINAT%'
                       AND UPPER(COALESCE(c.tipoPagamento,'')) LIKE '%FOB%')
            ORDER BY c.cteEmissao, c.cte
            """;

    private final JdbcTemplate jdbc;

    public LegacyInactiveClientRepository(@Qualifier("legacyJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<InactiveClientReport> findReport(long clientId, int year) {
        return client(clientId).map(c -> new InactiveClientReport(clientId, year, c[0], c[1], c[2], c[3], c[4],
                jdbc.query(CTE_SQL, (rs, row) -> mapCte(rs),
                        LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), clientId)));
    }

    @Override
    public Optional<InactiveClientReport> findReceivedReport(long clientId) {
        LocalDate until = LocalDate.now().plusDays(1);
        return client(clientId).map(c -> new InactiveClientReport(clientId, until.getYear(), RECEIVED_PERIOD,
                c[0], c[1], c[2], c[3], c[4],
                jdbc.query(RECEIVED_SQL, (rs, row) -> mapCte(rs), RECEIVED_SINCE, until, clientId)));
    }

    private Optional<String[]> client(long clientId) {
        List<String[]> client = jdbc.query(CLIENT_SQL, (rs, row) -> new String[] {
                rs.getString("razaoSocial"), rs.getString("fantasia"), rs.getString("cnpj"),
                rs.getString("cidade"), rs.getString("uf")}, clientId);
        return client.stream().findFirst();
    }

    private static InactiveClientReport.Cte mapCte(ResultSet rs) throws SQLException {
        return new InactiveClientReport.Cte(
                rs.getLong("cte"), toLocalDate(rs.getDate("cteEmissao")), rs.getString("tipoPagamento"),
                rs.getString("remetente"), rs.getString("remetenteCnpj"), rs.getString("remetenteCidade"),
                rs.getString("destinatario"), rs.getString("destinatarioCnpj"), rs.getString("destinatarioCidade"),
                rs.getString("notas"),
                rs.getLong("volumes"), decimal(rs.getBigDecimal("peso")), decimal(rs.getBigDecimal("valorNf")),
                decimal(rs.getBigDecimal("valorTotal")));
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
