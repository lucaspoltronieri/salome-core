package br.com.salome.core.infrastructure.legacy.torre;

import br.com.salome.core.application.torre.ConhecimentoLegadoRepository;
import br.com.salome.core.domain.torre.CteDescarga;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lê os CT-es de uma viagem (todos os manifestos do caminhão; somente leitura).
 * Joins/subselects de NFs reaproveitados de {@code LegacyManifestoBaixaRepository}.
 */
@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@ConditionalOnBean(JdbcTemplate.class)
public class LegacyConhecimentoRepository implements ConhecimentoLegadoRepository {

    private static final String SELECT = """
            SELECT c.idConhecimento, c.cte,
                   (SELECT GROUP_CONCAT(cnf.numero ORDER BY cnf.numero)
                      FROM conhecimentonotasfiscais cnf WHERE cnf.idConhecimento = c.idConhecimento) AS notasFiscais,
                   (SELECT SUM(IFNULL(cnf.quantidadeVolumes, 0))
                      FROM conhecimentonotasfiscais cnf WHERE cnf.idConhecimento = c.idConhecimento) AS volumes,
                   (SELECT SUM(IFNULL(cnf.pesoNf, 0))
                      FROM conhecimentonotasfiscais cnf WHERE cnf.idConhecimento = c.idConhecimento) AS peso,
                   CONCAT(rem.razaoSocial, ' - ', rem.cnpj_cpf) AS remetente,
                   CONCAT(dest.razaoSocial, ' - ', dest.cnpj_cpf) AS destinatario,
                   cidadeDest.descricao AS cidadeDestino
              FROM conhecimento c
              LEFT JOIN cliente rem ON rem.idCliente = c.idClienteEmitente
              LEFT JOIN cliente dest ON dest.idCliente = c.idClienteDestinatario
              LEFT JOIN cidade cidadeDest ON cidadeDest.idCidade = dest.idCidade
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyConhecimentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CteDescarga> listarCtesDaViagem(long idViagem) {
        // Cobre TODOS os manifestos (idViagemTransferencia) do mesmo caminhão
        // (idViagem): a descarga é por viagem, não por manifesto.
        String sql = SELECT + """
                 INNER JOIN viagemtransferenciaconhecimento vtc ON vtc.idConhecimento = c.idConhecimento
                 INNER JOIN viagemtransferencia vt ON vt.idViagemTransferencia = vtc.idViagemTransferencia
                 WHERE vt.idViagem = ?
                 ORDER BY c.cte
                """;
        return jdbcTemplate.query(sql, this::map, idViagem);
    }

    @Override
    public Optional<CteDescarga> buscarCte(long idConhecimento) {
        String sql = SELECT + " WHERE c.idConhecimento = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, idConhecimento));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CteDescarga> buscarCtePorChaveNf(String chaveNfe) {
        String sql = SELECT + """
                 WHERE c.cte IS NOT NULL
                   AND c.idConhecimento = (
                       SELECT cnf2.idConhecimento FROM conhecimentonotasfiscais cnf2
                        WHERE cnf2.chaveNFe = ? LIMIT 1)
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, chaveNfe));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, LocalDate> emissaoPorConhecimento(Collection<Long> idsConhecimento) {
        if (idsConhecimento.isEmpty()) {
            return Map.of();
        }
        String marcadores = String.join(",", java.util.Collections.nCopies(idsConhecimento.size(), "?"));
        String sql = "SELECT idConhecimento, cteEmissao FROM conhecimento WHERE idConhecimento IN (" + marcadores + ")";
        Map<Long, LocalDate> emissoes = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Date emissao = rs.getDate("cteEmissao");
            emissoes.put(rs.getLong("idConhecimento"), emissao == null ? null : emissao.toLocalDate());
        }, idsConhecimento.toArray());
        return emissoes;
    }

    private CteDescarga map(ResultSet rs, int rowNum) throws SQLException {
        int cte = rs.getInt("cte");
        return new CteDescarga(
                rs.getLong("idConhecimento"),
                rs.wasNull() ? null : cte,
                rs.getString("notasFiscais"),
                zero(rs.getBigDecimal("volumes")),
                zero(rs.getBigDecimal("peso")),
                rs.getString("remetente"),
                rs.getString("destinatario"),
                rs.getString("cidadeDestino"));
    }

    private BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
