package br.com.salome.core.infrastructure.legacy.financeiro;

import br.com.salome.core.application.financeiro.FluxoCaixaPrevistoRepository;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoFiltro;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoLancamento;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoStatus;
import br.com.salome.core.domain.notacompra.LegacyOrigin;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class LegacyFluxoCaixaPrevistoRepository implements FluxoCaixaPrevistoRepository {

    private static final LegacyOrigin ORIGIN = LegacyOrigin.of(
            "salome-legacy/view/NotaCompraDuplicatas.java",
            "formWindowOpened / btnBaixarActionPerformed / EmitirCheques",
            "fluxo caixa previsto query",
            "notacompraduplicatas, notacompra, extrato, banco, filial, fornecedor, planocontascentrocusto, planocontas, v_saldobancariotalao"
    );

    private final JdbcTemplate jdbcTemplate;

    public LegacyFluxoCaixaPrevistoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public BigDecimal consultarSaldoInicial(FluxoCaixaPrevistoFiltro filtro) {
        FluxoCaixaPrevistoFiltro efetivo = filtro == null ? FluxoCaixaPrevistoFiltro.padrao() : filtro;
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(saldoBancario), 0) AS saldoInicial
                FROM v_saldobancariotalao
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (efetivo.banco() != null) {
            sql.append(" AND CONCAT(COALESCE(idBanco, 0), ' ', COALESCE(descricao, '')) LIKE CONCAT('%', ?, '%')\n");
            args.add(efetivo.banco());
        }
        return jdbcTemplate.query(sql.toString(), rs -> rs.next() ? zero(rs.getBigDecimal("saldoInicial")) : BigDecimal.ZERO,
                args.toArray());
    }

    @Override
    public List<FluxoCaixaPrevistoLancamento> listarLancamentos(FluxoCaixaPrevistoFiltro filtro) {
        FluxoCaixaPrevistoFiltro efetivo = filtro == null ? FluxoCaixaPrevistoFiltro.padrao() : filtro;
        LocalDate dataLimite = efetivo.periodoOperacionalFim();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    nc.idNotaCompra,
                    ncd.idNotaCompraDuplicatas,
                    nc.notaFiscal,
                    nc.serieNF,
                    ncd.numero,
                    nc.idFilial,
                    fi.descricao AS filialNome,
                    nc.idFornecedor,
                    f.razaoSocial AS fornecedorNome,
                    COALESCE(ex.idBanco, 0) AS idBanco,
                    COALESCE(b.descricao, '') AS bancoNome,
                    COALESCE((SELECT pcc.idPlanoContasCentroCusto
                             FROM notacomprarateio nr
                             LEFT JOIN planocontascentrocusto pcc
                               ON pcc.idPlanoContasCentroCusto = nr.idPlanoContasCentroCusto
                             WHERE nr.idNotaCompra = nc.idNotaCompra
                             ORDER BY nr.idNotaCompraRateio
                             LIMIT 1), 0) AS idPlanoContasCentroCusto,
                    COALESCE((SELECT CONCAT(pc.classificacao, ' - ', pc.descricao)
                             FROM notacomprarateio nr
                             LEFT JOIN planocontascentrocusto pcc
                               ON pcc.idPlanoContasCentroCusto = nr.idPlanoContasCentroCusto
                             LEFT JOIN planocontas pc
                               ON pc.idPlanoContas = pcc.idPlanoContas
                             WHERE nr.idNotaCompra = nc.idNotaCompra
                             ORDER BY nr.idNotaCompraRateio
                             LIMIT 1), '') AS planoContasNome,
                    ncd.vencimento,
                    ncd.datapagamento,
                    COALESCE(ex.dataConciliacao, ex.data, ncd.datapagamento) AS dataRealizada,
                    ncd.valor AS valorPrevisto,
                    COALESCE(ncd.valorpago, 0) AS valorRealizado,
                    COALESCE(ex.historico, ncd.observacao, '') AS historico,
                    COALESCE(ncd.meioPagamento, '') AS meioPagamento,
                    CASE
                        WHEN ncd.datapagamento IS NOT NULL OR COALESCE(ncd.idExtrato, 0) > 0 THEN 'PAGO'
                        WHEN ncd.vencimento < CURRENT_DATE THEN 'ATRASADA'
                        WHEN ncd.vencimento = CURRENT_DATE THEN 'VENCE_HOJE'
                        WHEN ncd.vencimento > CURRENT_DATE THEN 'A_VENCER'
                        ELSE 'EM_ABERTO'
                    END AS status,
                    CASE
                        WHEN COALESCE(ncd.valorpago, 0) < 0 THEN 'ENTRADA'
                        ELSE 'SAIDA'
                    END AS direcao
                FROM notacompraduplicatas ncd
                INNER JOIN notacompra nc ON nc.idNotaCompra = ncd.idNotaCompra
                INNER JOIN fornecedor f ON f.idFornecedor = nc.idFornecedor
                LEFT JOIN filial fi ON fi.idFilial = nc.idFilial
                LEFT JOIN extrato ex ON ex.idExtrato = ncd.idExtrato
                LEFT JOIN banco b ON b.idBanco = ex.idBanco
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, efetivo, dataLimite);
        sql.append("""
                ORDER BY ncd.vencimento ASC, COALESCE(ncd.datapagamento, ncd.vencimento) ASC, nc.idNotaCompra DESC, ncd.idNotaCompraDuplicatas ASC
                """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapLancamento(rs), args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, FluxoCaixaPrevistoFiltro filtro, LocalDate dataLimite) {
        if (filtro.periodoInicio() != null && dataLimite != null) {
            sql.append("""
                    AND (
                        ncd.vencimento BETWEEN ? AND ?
                        OR ncd.datapagamento BETWEEN ? AND ?
                    )
                    """);
            args.add(filtro.periodoInicio());
            args.add(dataLimite);
            args.add(filtro.periodoInicio());
            args.add(dataLimite);
        }
        if (filtro.filial() != null) {
            sql.append(" AND CONCAT(COALESCE(nc.idFilial, 0), ' ', COALESCE(fi.descricao, '')) LIKE CONCAT('%', ?, '%')\n");
            args.add(filtro.filial());
        }
        if (filtro.fornecedor() != null) {
            sql.append(" AND CONCAT(COALESCE(nc.idFornecedor, 0), ' ', COALESCE(f.razaoSocial, '')) LIKE CONCAT('%', ?, '%')\n");
            args.add(filtro.fornecedor());
        }
        if (filtro.banco() != null) {
            sql.append(" AND CONCAT(COALESCE(ex.idBanco, 0), ' ', COALESCE(b.descricao, '')) LIKE CONCAT('%', ?, '%')\n");
            args.add(filtro.banco());
        }
        if (filtro.planoContas() != null) {
            sql.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM notacomprarateio nrx
                        LEFT JOIN planocontascentrocusto pccx ON pccx.idPlanoContasCentroCusto = nrx.idPlanoContasCentroCusto
                        LEFT JOIN planocontas pcx ON pcx.idPlanoContas = pccx.idPlanoContas
                        WHERE nrx.idNotaCompra = nc.idNotaCompra
                          AND CONCAT(COALESCE(pccx.idPlanoContasCentroCusto, 0), ' ', COALESCE(pcx.classificacao, ''), ' - ',
                              COALESCE(pcx.descricao, '')) LIKE CONCAT('%', ?, '%')
                    )
                    """);
            args.add(filtro.planoContas());
        }
        if (filtro.status() != null && filtro.status() != FluxoCaixaPrevistoStatus.TODAS) {
            appendStatusCondition(sql, args, filtro.status());
        }
    }

    private void appendStatusCondition(StringBuilder sql, List<Object> args, FluxoCaixaPrevistoStatus status) {
        sql.append(" AND ");
        switch (status) {
            case EM_ABERTO -> sql.append("(ncd.datapagamento IS NULL AND COALESCE(ncd.idExtrato, 0) = 0)");
            case ATRASADA -> sql.append("(ncd.datapagamento IS NULL AND ncd.vencimento < CURRENT_DATE)");
            case VENCE_HOJE -> sql.append("(ncd.datapagamento IS NULL AND ncd.vencimento = CURRENT_DATE)");
            case A_VENCER -> sql.append("(ncd.datapagamento IS NULL AND ncd.vencimento > CURRENT_DATE)");
            case PAGO -> sql.append("(ncd.datapagamento IS NOT NULL OR COALESCE(ncd.idExtrato, 0) > 0)");
            case TODAS -> {
            }
        }
    }

    private FluxoCaixaPrevistoLancamento mapLancamento(ResultSet rs) throws SQLException {
        BigDecimal valorPrevisto = zero(rs.getBigDecimal("valorPrevisto"));
        BigDecimal valorRealizado = zero(rs.getBigDecimal("valorRealizado"));
        boolean realizado = rs.getString("status") != null && "PAGO".equalsIgnoreCase(rs.getString("status"));
        if ("ENTRADA".equalsIgnoreCase(rs.getString("direcao"))) {
            valorPrevisto = valorPrevisto.negate();
            valorRealizado = valorRealizado.negate();
        }
        return new FluxoCaixaPrevistoLancamento(
                rs.getInt("idNotaCompra"),
                integerOrNull(rs, "idNotaCompraDuplicatas"),
                rs.getString("notaFiscal"),
                rs.getString("numero"),
                integerOrNull(rs, "idFilial"),
                rs.getString("filialNome"),
                integerOrNull(rs, "idFornecedor"),
                rs.getString("fornecedorNome"),
                integerOrNull(rs, "idBanco"),
                rs.getString("bancoNome"),
                integerOrNull(rs, "idPlanoContasCentroCusto"),
                rs.getString("planoContasNome"),
                toLocalDate(rs, "vencimento"),
                toLocalDate(rs, "dataRealizada"),
                valorPrevisto,
                valorRealizado,
                rs.getString("historico"),
                rs.getString("meioPagamento"),
                realizado,
                parseStatus(rs.getString("status")),
                "DocumentoEntradaDetalhesView",
                ORIGIN
        );
    }

    private FluxoCaixaPrevistoStatus parseStatus(String value) {
        if (value == null) {
            return FluxoCaixaPrevistoStatus.EM_ABERTO;
        }
        try {
            return FluxoCaixaPrevistoStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return FluxoCaixaPrevistoStatus.EM_ABERTO;
        }
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
