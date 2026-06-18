package br.com.salome.core.infrastructure.legacy.manifesto;

import br.com.salome.core.application.manifesto.ManifestoBaixaRepository;
import br.com.salome.core.application.manifesto.ManifestoRuleOrigins;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class LegacyManifestoBaixaRepository implements ManifestoBaixaRepository {

    private static final String DESTINATION_FILIAL_SQL = """
            (
                SELECT cr.idFilial
                FROM clienteregiaosetorlocal crsl
                INNER JOIN clienteregiaosetor crs ON crs.idClienteRegiaoSetor = crsl.idClienteRegiaoSetor
                INNER JOIN clienteregiao cr ON cr.idClienteRegiao = crs.idClienteRegiao
                WHERE crsl.cep = SUBSTRING(clienteDestinatario.cep, 1, 5)
                LIMIT 1
            )
            """;
    private static final String CURRENT_FILIAL_SQL = """
            (
                SELECT cr.idFilial
                FROM clienteregiaosetor crs
                INNER JOIN clienteregiao cr ON cr.idClienteRegiao = crs.idClienteRegiao
                WHERE crs.idClienteRegiaoSetor = c.idClienteRegiaoSetorAtual
                LIMIT 1
            )
            """;
    private static final String ARMAZEM_ATUAL_SQL = """
            (
                SELECT CONCAT(f.descricao, ' - ', COALESCE(cidadeArmazem.descricao, ''))
                FROM clienteregiaosetor crs
                INNER JOIN clienteregiao cr ON cr.idClienteRegiao = crs.idClienteRegiao
                INNER JOIN filial f ON f.idFilial = cr.idFilial
                LEFT JOIN cidade cidadeArmazem ON cidadeArmazem.idCidade = f.idCidade
                WHERE crs.idClienteRegiaoSetor = c.idClienteRegiaoSetorAtual
                LIMIT 1
            )
            """;
    private static final String DATA_ENTRADA_ARMAZEM_SQL = """
            (
                SELECT vtBaixa.dataBaixa
                FROM viagemtransferenciaconhecimento vtcBaixa
                INNER JOIN viagemtransferencia vtBaixa
                        ON vtBaixa.idViagemTransferencia = vtcBaixa.idViagemTransferencia
                WHERE vtcBaixa.idConhecimento = c.idConhecimento
                  AND vtBaixa.status = 'Baixado'
                  AND vtBaixa.idFilialDestino = ?
                  AND vtBaixa.dataBaixa IS NOT NULL
                ORDER BY vtBaixa.dataBaixa DESC, vtBaixa.horaBaixa DESC, vtBaixa.idViagemTransferencia DESC
                LIMIT 1
            )
            """;
    private static final String HORA_ENTRADA_ARMAZEM_SQL = """
            (
                SELECT vtBaixa.horaBaixa
                FROM viagemtransferenciaconhecimento vtcBaixa
                INNER JOIN viagemtransferencia vtBaixa
                        ON vtBaixa.idViagemTransferencia = vtcBaixa.idViagemTransferencia
                WHERE vtcBaixa.idConhecimento = c.idConhecimento
                  AND vtBaixa.status = 'Baixado'
                  AND vtBaixa.idFilialDestino = ?
                  AND vtBaixa.dataBaixa IS NOT NULL
                ORDER BY vtBaixa.dataBaixa DESC, vtBaixa.horaBaixa DESC, vtBaixa.idViagemTransferencia DESC
                LIMIT 1
            )
            """;
    private static final String EMPTY_ENTRADA_ARMAZEM_SQL = "NULL";
    private static final String CIDADE_DESTINO_SQL = "cidadeDestinatario.descricao";
    private static final String SETOR_REGIAO_SQL = """
            (
                SELECT CONCAT(crs.descricao, ' ( ', cr.descricao, ' ) ')
                FROM clienteregiaosetorlocal crsl
                INNER JOIN clienteregiaosetor crs ON crs.idClienteRegiaoSetor = crsl.idClienteRegiaoSetor
                INNER JOIN clienteregiao cr ON cr.idClienteRegiao = crs.idClienteRegiao
                WHERE crsl.cep = SUBSTRING(clienteDestinatario.cep, 1, 5)
                LIMIT 1
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyManifestoBaixaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Integer> buscarFilialDestinoSaoJoseRioPreto() {
        return jdbcTemplate.query("""
                SELECT f.idFilial
                FROM filial f
                LEFT JOIN cidade c ON c.idCidade = f.idCidade
                WHERE UPPER(f.descricao) LIKE '%SALOME%'
                  AND UPPER(COALESCE(c.descricao, '')) LIKE '%RIO PRETO%'
                  AND COALESCE(f.idClienteRegiaoSetorArmazem, 0) > 0
                ORDER BY f.idFilial
                LIMIT 1
                """, (rs, rowNum) -> rs.getInt("idFilial")).stream().findFirst();
    }

    @Override
    public List<CteMapaSjpRecord> listarArmazemSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        String sql = baseSelect(DATA_ENTRADA_ARMAZEM_SQL, HORA_ENTRADA_ARMAZEM_SQL, emptyTripFields()) + """
                FROM conhecimento c
                %s
                WHERE c.cte IS NOT NULL
                  AND c.cteEmissao >= ?
                  AND UPPER(COALESCE(c.situacao, '')) LIKE 'ARMAZ%%'
                  AND %s = ?
                  AND %s = ?
                ORDER BY c.cteEmissao, c.cte
                LIMIT ?
                """.formatted(commonJoins(), CURRENT_FILIAL_SQL, DESTINATION_FILIAL_SQL);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapCte(rs, ManifestoRuleOrigins.BAIXA_MANIFESTO),
                filialDestinoId, filialDestinoId, dataCorte, filialDestinoId, filialDestinoId, limite);
    }

    @Override
    public List<CteMapaSjpRecord> listarEmRotaEntrega(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        String sql = baseSelect(EMPTY_ENTRADA_ARMAZEM_SQL, EMPTY_ENTRADA_ARMAZEM_SQL, """
                    ve.idViagemEntrega idManifestoTransferencia,
                    ve.idViagem,
                    NULL filialOrigem,
                    viagemEntrega.dataPrevistaInicio dataPrevisaoSaida,
                    viagemEntrega.horaPrevistaInicio horaPrevisaoSaida,
                    veiculoEntrega.placa placaVeiculo,
                    fornecedorMotorista.razaoSocial motorista,
                    viagemEntrega.dataPrevistaFim dataPrevisaoChegada,
                    viagemEntrega.horaPrevistaFim horaPrevisaoChegada
                """) + """
                FROM conhecimento c
                %s
                INNER JOIN viagementrega ve
                        ON ve.idViagemEntrega = (
                            SELECT veUltima.idViagemEntrega
                            FROM viagementrega veUltima
                            WHERE veUltima.idConhecimento = c.idConhecimento
                              AND veUltima.entregaRealizada = 'N\u00e3o'
                            ORDER BY veUltima.idViagemEntrega DESC
                            LIMIT 1
                        )
                INNER JOIN viagem viagemEntrega ON viagemEntrega.idViagem = ve.idViagem
                LEFT JOIN veiculo veiculoEntrega ON veiculoEntrega.idVeiculo = viagemEntrega.idVeiculo
                LEFT JOIN motorista motoristaEntrega ON motoristaEntrega.idMotorista = viagemEntrega.idMotorista
                LEFT JOIN fornecedor fornecedorMotorista ON fornecedorMotorista.idFornecedor = motoristaEntrega.idFornecedor
                WHERE c.cte IS NOT NULL
                  AND c.cteEmissao >= ?
                  AND UPPER(COALESCE(c.situacao, '')) = 'EM VIAGEM'
                  AND %s = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM viagemtransferenciaconhecimento vtcAtiva
                      INNER JOIN viagemtransferencia vtAtiva
                              ON vtAtiva.idViagemTransferencia = vtcAtiva.idViagemTransferencia
                      WHERE vtcAtiva.idConhecimento = c.idConhecimento
                        AND vtAtiva.status = 'Em Viagem'
                        AND vtAtiva.idFilialDestino = ?
                  )
                ORDER BY viagemEntrega.dataPrevistaInicio, viagemEntrega.horaPrevistaInicio, c.cte
                LIMIT ?
                """.formatted(commonJoins(), DESTINATION_FILIAL_SQL);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapCte(rs, ManifestoRuleOrigins.VIAGEM_ENTREGA),
                dataCorte, filialDestinoId, filialDestinoId, limite);
    }

    @Override
    public List<CteMapaSjpRecord> listarOutrosArmazens(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        String sql = baseSelect(EMPTY_ENTRADA_ARMAZEM_SQL, EMPTY_ENTRADA_ARMAZEM_SQL, emptyTripFields()) + """
                FROM conhecimento c
                %s
                WHERE c.cte IS NOT NULL
                  AND c.cteEmissao >= ?
                  AND UPPER(COALESCE(c.situacao, '')) LIKE 'ARMAZ%%'
                  AND %s = ?
                  AND COALESCE(%s, 0) <> ?
                ORDER BY armazemAtual, c.cteEmissao, c.cte
                LIMIT ?
                """.formatted(commonJoins(), DESTINATION_FILIAL_SQL, CURRENT_FILIAL_SQL);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapCte(rs, ManifestoRuleOrigins.CTE_ARMAZEM),
                dataCorte, filialDestinoId, filialDestinoId, limite);
    }

    @Override
    public List<CteMapaSjpRecord> listarViagensParaSjp(Integer filialDestinoId, LocalDate dataCorte, int limite) {
        String sql = baseSelect(EMPTY_ENTRADA_ARMAZEM_SQL, EMPTY_ENTRADA_ARMAZEM_SQL, """
                    vt.idViagemTransferencia idManifestoTransferencia,
                    vt.idViagem,
                    CONCAT(filialOrigem.descricao, ' - ', COALESCE(cidadeFilialOrigem.descricao, '')) filialOrigem,
                    viagemTransferencia.dataPrevistaInicio dataPrevisaoSaida,
                    viagemTransferencia.horaPrevistaInicio horaPrevisaoSaida,
                    veiculoTransferencia.placa placaVeiculo,
                    fornecedorMotorista.razaoSocial motorista,
                    viagemTransferencia.dataPrevistaFim dataPrevisaoChegada,
                    viagemTransferencia.horaPrevistaFim horaPrevisaoChegada
                """) + """
                FROM viagemtransferenciaconhecimento vtc
                INNER JOIN viagemtransferencia vt ON vt.idViagemTransferencia = vtc.idViagemTransferencia
                INNER JOIN conhecimento c ON c.idConhecimento = vtc.idConhecimento
                %s
                INNER JOIN viagem viagemTransferencia ON viagemTransferencia.idViagem = vt.idViagem
                INNER JOIN filial filialOrigem ON filialOrigem.idFilial = vt.idFilialOrigem
                LEFT JOIN cidade cidadeFilialOrigem ON cidadeFilialOrigem.idCidade = filialOrigem.idCidade
                LEFT JOIN veiculo veiculoTransferencia ON veiculoTransferencia.idVeiculo = viagemTransferencia.idVeiculo
                LEFT JOIN motorista motoristaTransferencia ON motoristaTransferencia.idMotorista = viagemTransferencia.idMotorista
                LEFT JOIN fornecedor fornecedorMotorista ON fornecedorMotorista.idFornecedor = motoristaTransferencia.idFornecedor
                WHERE c.cte IS NOT NULL
                  AND c.cteEmissao >= ?
                  AND UPPER(COALESCE(c.situacao, '')) = 'EM VIAGEM'
                  AND vt.status = 'Em Viagem'
                  AND vt.idFilialDestino = ?
                  AND vt.idFilialOrigem <> ?
                  AND %s = ?
                ORDER BY viagemTransferencia.dataPrevistaInicio, viagemTransferencia.horaPrevistaInicio, vt.idViagemTransferencia, c.cte
                LIMIT ?
                """.formatted(commonJoins(), DESTINATION_FILIAL_SQL);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapCte(rs, ManifestoRuleOrigins.VIAGEM_TRANSFERENCIA),
                dataCorte, filialDestinoId, filialDestinoId, filialDestinoId, limite);
    }

    private String baseSelect(String dataEntradaArmazemField, String horaEntradaArmazemField, String tripFields) {
        return """
                SELECT
                    c.idConhecimento,
                    c.cte,
                    %s dataEntradaArmazem,
                    %s horaEntradaArmazem,
                    c.cteEmissao dataEmissao,
                    c.dataPrevistaEntrega dataPrevistaEntrega,
                    c.situacao situacaoCte,
                    CONCAT(filialEmissao.descricao, ' - ', COALESCE(cidadeFilialEmissao.descricao, '')) filialEmissao,
                    CONCAT(clienteRemetente.razaoSocial, ' - ', clienteRemetente.cnpj_cpf) remetente,
                    CONCAT(clienteDestinatario.razaoSocial, ' - ', clienteDestinatario.cnpj_cpf) destinatario,
                    %s cidadeDestinatario,
                    %s setorRegiao,
                    (
                        SELECT GROUP_CONCAT(cnf.numero ORDER BY cnf.numero)
                        FROM conhecimentonotasfiscais cnf
                        WHERE cnf.idConhecimento = c.idConhecimento
                    ) notasFiscais,
                    (
                        SELECT SUM(IFNULL(cnf.quantidadeVolumes, 0))
                        FROM conhecimentonotasfiscais cnf
                        WHERE cnf.idConhecimento = c.idConhecimento
                    ) quantidadeVolumes,
                    (
                        SELECT SUM(IFNULL(cnf.pesoNf, 0))
                        FROM conhecimentonotasfiscais cnf
                        WHERE cnf.idConhecimento = c.idConhecimento
                    ) peso,
                    (
                        SELECT SUM(IFNULL(cnf.valorNF, 0))
                        FROM conhecimentonotasfiscais cnf
                        WHERE cnf.idConhecimento = c.idConhecimento
                    ) valorNf,
                    c.valorTotal valorTotalCte,
                    %s armazemAtual,
                    %s
                """.formatted(dataEntradaArmazemField, horaEntradaArmazemField, CIDADE_DESTINO_SQL,
                SETOR_REGIAO_SQL, ARMAZEM_ATUAL_SQL, tripFields);
    }

    private String commonJoins() {
        return """
                LEFT JOIN filial filialEmissao ON filialEmissao.idFilial = c.idFilial
                LEFT JOIN cidade cidadeFilialEmissao ON cidadeFilialEmissao.idCidade = filialEmissao.idCidade
                LEFT JOIN cliente clienteRemetente ON clienteRemetente.idCliente = c.idClienteEmitente
                LEFT JOIN cliente clienteDestinatario ON clienteDestinatario.idCliente = c.idClienteDestinatario
                LEFT JOIN cidade cidadeDestinatario ON cidadeDestinatario.idCidade = clienteDestinatario.idCidade
                """;
    }

    private String emptyTripFields() {
        return """
                    NULL idManifestoTransferencia,
                    NULL idViagem,
                    NULL filialOrigem,
                    NULL dataPrevisaoSaida,
                    NULL horaPrevisaoSaida,
                    NULL placaVeiculo,
                    NULL motorista,
                    NULL dataPrevisaoChegada,
                    NULL horaPrevisaoChegada
                """;
    }

    private CteMapaSjpRecord mapCte(ResultSet rs, LegacyOrigin origin) throws SQLException {
        return new CteMapaSjpRecord(
                rs.getInt("idConhecimento"),
                integerOrNull(rs, "cte"),
                toLocalDate(rs, "dataEntradaArmazem"),
                rs.getString("horaEntradaArmazem"),
                toLocalDate(rs, "dataEmissao"),
                toLocalDate(rs, "dataPrevistaEntrega"),
                rs.getString("situacaoCte"),
                rs.getString("filialEmissao"),
                rs.getString("remetente"),
                rs.getString("destinatario"),
                rs.getString("cidadeDestinatario"),
                rs.getString("setorRegiao"),
                rs.getString("notasFiscais"),
                zero(rs.getBigDecimal("quantidadeVolumes")),
                zero(rs.getBigDecimal("peso")),
                zero(rs.getBigDecimal("valorNf")),
                zero(rs.getBigDecimal("valorTotalCte")),
                rs.getString("armazemAtual"),
                integerOrNull(rs, "idManifestoTransferencia"),
                integerOrNull(rs, "idViagem"),
                rs.getString("filialOrigem"),
                toLocalDate(rs, "dataPrevisaoSaida"),
                rs.getString("horaPrevisaoSaida"),
                rs.getString("placaVeiculo"),
                rs.getString("motorista"),
                toLocalDate(rs, "dataPrevisaoChegada"),
                rs.getString("horaPrevisaoChegada"),
                origin
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
