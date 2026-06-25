package br.com.salome.core.infrastructure.legacy.torre;

import br.com.salome.core.application.torre.ViagemLegadoRepository;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lê do legado as viagens de transferência baixadas (chegadas) e destinadas à
 * filial, a partir da data de corte. Somente leitura; usa o {@code JdbcTemplate}
 * @Primary (legado). Joins reaproveitados de {@code LegacyManifestoBaixaRepository}.
 */
@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@ConditionalOnBean(JdbcTemplate.class)
public class LegacyViagemRepository implements ViagemLegadoRepository {

    private static final String SQL = """
            SELECT vt.idViagemTransferencia                                         AS idViagemTransferencia,
                   vt.idViagem                                                      AS idViagem,
                   vt.dataBaixa                                                     AS dataBaixa,
                   vt.horaBaixa                                                     AS horaBaixa,
                   veiculo.placa                                                    AS placa,
                   fornecedorMotorista.razaoSocial                                  AS motorista,
                   CONCAT(filialOrigem.descricao, ' - ', COALESCE(cidadeOrigem.descricao, '')) AS origem,
                   COUNT(DISTINCT c.idConhecimento)                                 AS qtdCtes,
                   COALESCE(SUM(IFNULL(cnf.quantidadeVolumes, 0)), 0)               AS volumes,
                   COALESCE(SUM(IFNULL(cnf.pesoNf, 0)), 0)                          AS peso
              FROM viagemtransferencia vt
              INNER JOIN viagem v ON v.idViagem = vt.idViagem
              INNER JOIN filial filialOrigem ON filialOrigem.idFilial = vt.idFilialOrigem
              LEFT JOIN cidade cidadeOrigem ON cidadeOrigem.idCidade = filialOrigem.idCidade
              LEFT JOIN veiculo ON veiculo.idVeiculo = v.idVeiculo
              LEFT JOIN motorista ON motorista.idMotorista = v.idMotorista
              LEFT JOIN fornecedor fornecedorMotorista ON fornecedorMotorista.idFornecedor = motorista.idFornecedor
              LEFT JOIN viagemtransferenciaconhecimento vtc ON vtc.idViagemTransferencia = vt.idViagemTransferencia
              LEFT JOIN conhecimento c ON c.idConhecimento = vtc.idConhecimento
              LEFT JOIN conhecimentonotasfiscais cnf ON cnf.idConhecimento = c.idConhecimento
             WHERE vt.idFilialDestino = ?
               AND vt.status = 'Baixado'
               AND vt.dataBaixa IS NOT NULL
               AND vt.dataBaixa >= ?
             GROUP BY vt.idViagemTransferencia, vt.idViagem, vt.dataBaixa, vt.horaBaixa, placa, motorista, origem
             ORDER BY vt.dataBaixa DESC, vt.horaBaixa DESC
             LIMIT ?
            """;

    // Coletas da própria filial: a coleta é lançada na viagem (ViagemColetas) e fica
    // 'Em Viagem' enquanto o caminhão vem pra filial. Agrupado por viagem (caminhão).
    private static final String SQL_COLETAS = """
            SELECT v.idViagem                                       AS idViagem,
                   veiculo.placa                                    AS placa,
                   fornecedorMotorista.razaoSocial                  AS motorista,
                   COUNT(DISTINCT col.idColeta)                     AS qtdColetas,
                   COALESCE(SUM(IFNULL(col.quantidadeVolumes, 0)), 0) AS volumes,
                   COALESCE(SUM(IFNULL(col.pesoNf, 0)), 0)          AS peso,
                   MAX(col.statusData)                              AS data
              FROM ViagemColetas vc
              INNER JOIN Coleta col ON col.idColeta = vc.idColeta
              INNER JOIN viagem v ON v.idViagem = vc.idViagem
              LEFT JOIN veiculo ON veiculo.idVeiculo = v.idVeiculo
              LEFT JOIN motorista ON motorista.idMotorista = v.idMotorista
              LEFT JOIN fornecedor fornecedorMotorista ON fornecedorMotorista.idFornecedor = motorista.idFornecedor
             WHERE col.idFilial = ?
               AND col.status = 'Em Viagem'
             GROUP BY v.idViagem, veiculo.placa, fornecedorMotorista.razaoSocial
             ORDER BY data DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyViagemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ViagemAguardando> listarAguardandoDescarga(int idFilialDestino, LocalDate dataCorte, int limite) {
        return jdbcTemplate.query(SQL, (rs, n) -> new ViagemAguardando(
                rs.getLong("idViagemTransferencia"),
                rs.getObject("idViagem", Long.class),
                rs.getDate("dataBaixa") == null ? null : rs.getDate("dataBaixa").toLocalDate(),
                rs.getString("horaBaixa"),
                rs.getString("placa"),
                rs.getString("motorista"),
                rs.getString("origem"),
                rs.getInt("qtdCtes"),
                zero(rs.getBigDecimal("volumes")),
                zero(rs.getBigDecimal("peso"))
        ), idFilialDestino, dataCorte, limite);
    }

    @Override
    public List<ViagemAguardando> listarColetasAguardando(int idFilial, int limite) {
        return jdbcTemplate.query(SQL_COLETAS, (rs, n) -> {
            long idViagem = rs.getLong("idViagem");
            return new ViagemAguardando(
                    idViagem, // sem manifesto; a descarga de coleta abre pela viagem
                    idViagem,
                    rs.getDate("data") == null ? null : rs.getDate("data").toLocalDate(),
                    null,
                    rs.getString("placa"),
                    rs.getString("motorista"),
                    "Coleta",
                    rs.getInt("qtdColetas"),
                    zero(rs.getBigDecimal("volumes")),
                    zero(rs.getBigDecimal("peso")));
        }, idFilial, limite);
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
