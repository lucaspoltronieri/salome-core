package br.com.salome.core.infrastructure.legacy.financeiro;

import br.com.salome.core.application.financeiro.FinanceiroFluxoCaixaRepository;
import br.com.salome.core.application.financeiro.FinanceiroRuleOrigins;
import br.com.salome.core.domain.financeiro.DreClienteDriver;
import br.com.salome.core.domain.financeiro.DreFaturaCte;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import br.com.salome.core.domain.financeiro.FinanceiroMovimento;
import br.com.salome.core.domain.financeiro.FinanceiroNatureza;
import br.com.salome.core.domain.financeiro.FinanceiroOrigemTipo;
import br.com.salome.core.domain.financeiro.FinanceiroSaldoBanco;
import br.com.salome.core.domain.financeiro.FinanceiroStatus;
import br.com.salome.core.domain.financeiro.PlanoConta;
import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "true")
public class LegacyFinanceiroFluxoCaixaRepository implements FinanceiroFluxoCaixaRepository {

    private static final int BANCO_PERDAS_DANOS_ID = 34;
    private static final String BANCOS_FLUXO_IDS = "15,18,36,37,40,41";
    private static final String CONTAS_CAIXA_DINHEIRO_IDS = "23,24,25,27,28,33";
    private static final String CLIENTE_EXPRESSO_SALOME_SQL = """
            SELECT idCliente,
                   REPLACE(REPLACE(REPLACE(COALESCE(cnpj_cpf, ''), '.', ''), '/', ''), '-', '') cnpjLimpo
            FROM cliente
            WHERE UPPER(COALESCE(razaoSocial, '')) LIKE '%EXPRESSO%SALOME%'
               OR UPPER(COALESCE(fantasia, '')) LIKE '%EXPRESSO%SALOME%'
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyFinanceiroFluxoCaixaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FinanceiroMovimento> listarMovimentos(FinanceiroFiltro filtro) {
        FinanceiroFiltro normalizado = filtro.normalizado();
        List<FinanceiroMovimento> movimentos = new ArrayList<>();
        movimentos.addAll(listarDuplicatasNotaCompra(normalizado));
        movimentos.addAll(listarCaixaDinheiro(normalizado));
        movimentos.addAll(listarPagamentosCaixa(normalizado));
        movimentos.addAll(listarExtratoAvulso(normalizado));
        movimentos.addAll(listarFaturasBaixadas(normalizado));
        movimentos.addAll(listarFaturasAbertas(normalizado));
        movimentos.addAll(listarCtesAbertos(normalizado));
        return movimentos;
    }

    @Override
    public List<FinanceiroMovimento> listarMovimentosCompetencia(FinanceiroFiltro filtro) {
        FinanceiroFiltro normalizado = filtro.normalizado();
        List<FinanceiroMovimento> movimentos = new ArrayList<>();
        movimentos.addAll(listarReceitasCteCompetencia(normalizado));
        movimentos.addAll(listarDespesasNotaCompraCompetencia(normalizado));
        movimentos.addAll(listarCaixaDinheiro(normalizado));
        movimentos.addAll(listarPagamentosCaixa(normalizado));
        movimentos.addAll(listarExtratoAvulso(normalizado));
        return movimentos;
    }

    @Override
    public List<PlanoConta> listarPlanoContas() {
        return jdbcTemplate.query("""
                SELECT
                    pc.classificacao classificacao,
                    pc.descricao descricao,
                    IF(UPPER(COALESCE(pc.tipo, '')) LIKE 'SINT%', 1, 0) sintetica,
                    MAX(IF(UPPER(COALESCE(pccc.impostosFinanceiro, '')) = 'SIM', 1, 0)) impostosFinanceiro
                FROM planocontas pc
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContas = pc.idPlanoContas
                WHERE COALESCE(pc.classificacao, '') <> ''
                GROUP BY pc.classificacao, pc.descricao, sintetica
                """, (rs, rowNum) -> new PlanoConta(rs.getString("classificacao"), rs.getString("descricao"),
                rs.getInt("sintetica") == 1, rs.getInt("impostosFinanceiro") == 1));
    }

    @Override
    public java.util.Map<String, Integer> listarFilialPorPlaca() {
        java.util.Map<String, Integer> mapa = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT UPPER(REPLACE(REPLACE(COALESCE(placa, ''), '-', ''), ' ', '')) placa, idFilial
                FROM veiculo
                WHERE COALESCE(placa, '') <> '' AND COALESCE(idFilial, 0) > 0
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> mapa.put(rs.getString("placa"), rs.getInt("idFilial")));
        return mapa;
    }

    @Override
    public java.util.List<br.com.salome.core.domain.financeiro.RepasseInterFilial> listarRepasseTransferencia(
            FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT c.idFilial origem, vt.idFilialDestino destino, SUM(c.valorTotal) frete
                FROM conhecimento c
                JOIN viagemtransferenciaconhecimento vtc ON vtc.idConhecimento = c.idConhecimento
                JOIN viagemtransferencia vt ON vt.idViagemTransferencia = vtc.idViagemTransferencia
                WHERE c.cteEmissao BETWEEN ? AND ?
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%'
                  AND UPPER(COALESCE(c.tipoPagamento, '')) NOT LIKE 'CORTES%'
                  AND COALESCE(c.idFilial, 0) > 0
                  AND COALESCE(vt.idFilialDestino, 0) > 0
                  AND vt.idFilialDestino <> c.idFilial
                  AND vtc.idViagemTransferencia = (
                      SELECT MAX(vtc2.idViagemTransferencia)
                      FROM viagemtransferenciaconhecimento vtc2
                      WHERE vtc2.idConhecimento = c.idConhecimento
                  )
                GROUP BY c.idFilial, vt.idFilialDestino
                """, (rs, rowNum) -> new br.com.salome.core.domain.financeiro.RepasseInterFilial(
                integerOrNull(rs, "origem"), integerOrNull(rs, "destino"), decimal(rs, "frete")),
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
    }

    @Override
    public java.util.Map<Integer, BigDecimal> listarPesoPorFilial(FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        java.util.Map<Integer, BigDecimal> mapa = new java.util.LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT c.idFilial filial, COALESCE(SUM(nf.peso), 0) / 1000 toneladas
                FROM conhecimento c
                LEFT JOIN (
                    SELECT idConhecimento, SUM(pesoNf) peso
                    FROM ConhecimentoNotasFiscais
                    GROUP BY idConhecimento
                ) nf ON nf.idConhecimento = c.idConhecimento
                WHERE c.cteEmissao BETWEEN ? AND ?
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%'
                  AND UPPER(COALESCE(c.tipoPagamento, '')) NOT LIKE 'CORTES%'
                  AND COALESCE(c.idFilial, 0) > 0
                GROUP BY c.idFilial
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> mapa.put(rs.getInt("filial"), decimal(rs, "toneladas")),
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
        return mapa;
    }

    /**
     * Saldo por banco calculado a partir do {@code extrato} (as colunas {@code banco.saldo}/{@code saldo_extr}
     * estao defasadas e {@code extrato.saldo}/{@code saldo_extr} sao sempre nulos no legado). Debito conta
     * negativo e credito positivo. O {@code saldoBancario} (usado como base do fluxo) considera somente os
     * lancamentos conciliados ({@code dataConciliacao} preenchida) — e o "Saldo Bancario" da tela de Extrato;
     * o {@code saldoOperacional} considera todos os lancamentos (inclui pendentes), como o "Saldo" da tela.
     */
    @Override
    public List<FinanceiroSaldoBanco> listarSaldosBancarios(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    b.idBanco bancoId,
                    CONCAT(IFNULL(b.conta, ''), IF(IFNULL(b.nome, '') = '', '', CONCAT(' - ', b.nome))) banco,
                    COALESCE((
                        SELECT SUM(CASE WHEN UPPER(COALESCE(e.deb_credt, '')) LIKE 'D%%' THEN -e.valor ELSE e.valor END)
                        FROM extrato e WHERE e.idBanco = b.idBanco
                    ), 0) saldoOperacional,
                    COALESCE((
                        SELECT SUM(CASE WHEN UPPER(COALESCE(e.deb_credt, '')) LIKE 'D%%' THEN -e.valor ELSE e.valor END)
                        FROM extrato e WHERE e.idBanco = b.idBanco AND e.dataConciliacao IS NOT NULL
                    ), 0) saldoBancario,
                    COALESCE(b.limite, 0) limite,
                    IF(UPPER(COALESCE(b.contaCaixa, '')) = 'SIM', 1, 0) contaCaixa
                FROM banco b
                WHERE b.idBanco IN (%s)
                ORDER BY contaCaixa DESC, b.conta, b.nome
                """.formatted(BANCOS_FLUXO_IDS), (rs, rowNum) -> new FinanceiroSaldoBanco(integerOrNull(rs, "bancoId"), rs.getString("banco"),
                decimalComSinal(rs, "saldoOperacional"), decimalComSinal(rs, "saldoBancario"), decimal(rs, "limite"),
                rs.getInt("contaCaixa") == 1));
    }

    private List<FinanceiroMovimento> listarDuplicatasNotaCompra(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    ncd.idNotaCompraDuplicatas origemId,
                    nc.dataEntrada dataCompetencia,
                    ncd.vencimento dataVencimento,
                    ncd.datapagamento dataBaixa,
                    IFNULL(NULLIF(ncd.valorpago, 0), ncd.valor)
                        * IFNULL(rateio.peso / NULLIF(rateio.totalPeso, 0), 1) valor,
                    e.idBanco bancoId,
                    b.conta banco,
                    nc.idFornecedor pessoaId,
                    fornecedor.razaoSocial pessoa,
                    pccc.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    COALESCE(rateio.idFilial, nc.idFilial) filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    rateio.idPlanoContasCentroCusto planoContasCentroCustoId,
                    pc.descricao planoContas,
                    pc.classificacao classificacao,
                    pc.dmr dmr,
                    CONCAT('NC-', nc.idNotaCompra, '/', ncd.numero) documento,
                    CONCAT('Nota compra ', nc.notaFiscal, ' - ', IFNULL(ncd.observacao, '')) historico
                FROM notacompraduplicatas ncd
                INNER JOIN notacompra nc ON nc.idNotaCompra = ncd.idNotaCompra
                LEFT JOIN fornecedor ON fornecedor.idFornecedor = nc.idFornecedor
                LEFT JOIN extrato e ON e.idExtrato = ncd.idExtrato
                LEFT JOIN banco b ON b.idBanco = e.idBanco
                LEFT JOIN (
                    SELECT
                        nr.idNotaCompra,
                        nr.idFilial,
                        nr.idPlanoContasCentroCusto,
                        nr.valor peso,
                        (SELECT SUM(nr2.valor) FROM notacomprarateio nr2 WHERE nr2.idNotaCompra = nr.idNotaCompra) totalPeso
                    FROM notacomprarateio nr
                    WHERE COALESCE(nr.valor, 0) > 0
                    UNION ALL
                    SELECT
                        ncp.idNotaCompra,
                        ncp.idFilial,
                        ncp.idPlanoContasCentroCusto,
                        ncp.totalLiquido peso,
                        (SELECT SUM(ncp2.totalLiquido) FROM notacompraprodutos ncp2 WHERE ncp2.idNotaCompra = ncp.idNotaCompra) totalPeso
                    FROM notacompraprodutos ncp
                    WHERE COALESCE(ncp.totalLiquido, 0) > 0
                      AND NOT EXISTS (
                          SELECT 1
                          FROM notacomprarateio nr
                          WHERE nr.idNotaCompra = ncp.idNotaCompra
                            AND COALESCE(nr.valor, 0) > 0
                      )
                ) rateio ON rateio.idNotaCompra = nc.idNotaCompra
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = rateio.idPlanoContasCentroCusto
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                LEFT JOIN planocontas pc ON pc.idPlanoContas = pccc.idPlanoContas
                LEFT JOIN filial fil ON fil.idFilial = COALESCE(rateio.idFilial, nc.idFilial)
                WHERE (
                    ncd.vencimento BETWEEN ? AND ?
                    OR ncd.datapagamento BETWEEN ? AND ?
                    OR (ncd.vencimento IS NULL AND ncd.datapagamento IS NULL AND nc.dataEntrada BETWEEN ? AND ?)
                )
                  AND (
                      ncd.datapagamento IS NULL
                      OR e.idBanco IN (%s)
                  )
                """.formatted(BANCOS_FLUXO_IDS), (rs, rowNum) -> movimentoDespesa(rs, FinanceiroOrigemTipo.NOTA_COMPRA_DUPLICATA,
                rs.getDate("dataBaixa") == null ? FinanceiroStatus.PREVISTO : FinanceiroStatus.REALIZADO,
                FinanceiroRuleOrigins.NOTA_COMPRA_DUPLICATA), Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()),
                Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()), Date.valueOf(filtro.inicio()),
                Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarPagamentosCaixa(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    pcx.idPagamentoCaixa origemId,
                    pcx.dataLancamento dataCompetencia,
                    pcx.dataVencimento dataVencimento,
                    COALESCE(pcx.dataBaixa, pcx.dataVencimento) dataBaixa,
                    pcx.valorDocumento valor,
                    pcx.idBanco bancoId,
                    b.conta banco,
                    pcx.idFornecedor pessoaId,
                    fornecedor.razaoSocial pessoa,
                    pcx.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    NULL filialId,
                    NULL filial,
                    pcx.idPlanoContasCentroCusto planoContasCentroCustoId,
                    plano.descricao planoContas,
                    plano.classificacao classificacao,
                    plano.dmr dmr,
                    CONCAT('PCX-', pcx.idPagamentoCaixa, ' / ', IFNULL(pcx.numeroDocumento, '')) documento,
                    pcx.observacao historico
                FROM pagamentocaixa pcx
                LEFT JOIN banco b ON b.idBanco = pcx.idBanco
                LEFT JOIN fornecedor ON fornecedor.idFornecedor = pcx.idFornecedor
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pcx.idCentroCusto
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = pcx.idPlanoContasCentroCusto
                LEFT JOIN planocontas plano ON plano.idPlanoContas = pccc.idPlanoContas
                WHERE (
                    pcx.dataVencimento BETWEEN ? AND ?
                    OR pcx.dataBaixa BETWEEN ? AND ?
                    OR (pcx.dataVencimento IS NULL AND pcx.dataBaixa IS NULL AND pcx.dataLancamento BETWEEN ? AND ?)
                )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM caixa cx
                      WHERE cx.idPagamentoCaixa = pcx.idPagamentoCaixa
                        AND UPPER(COALESCE(cx.tipoMovimento, '')) LIKE 'SA%%'
                  )
                  AND (
                      pcx.dataBaixa IS NULL
                      OR pcx.idBanco IN (%s)
                  )
                """.formatted(BANCOS_FLUXO_IDS), (rs, rowNum) -> movimentoDespesa(rs, FinanceiroOrigemTipo.PAGAMENTO_CAIXA,
                FinanceiroStatus.REALIZADO, FinanceiroRuleOrigins.PAGAMENTO_CAIXA),
                Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()),
                Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()), Date.valueOf(filtro.inicio()),
                Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarCaixaDinheiro(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    cx.idCaixa origemId,
                    cx.dataLancamento dataCompetencia,
                    COALESCE(pcx.dataVencimento, cx.dataLancamento) dataVencimento,
                    cx.dataLancamento dataBaixa,
                    cx.valor valor,
                    cx.idBanco bancoId,
                    b.conta banco,
                    pcx.idFornecedor pessoaId,
                    fornecedor.razaoSocial pessoa,
                    pcx.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    NULL filialId,
                    NULL filial,
                    pcx.idPlanoContasCentroCusto planoContasCentroCustoId,
                    plano.descricao planoContas,
                    plano.classificacao classificacao,
                    plano.dmr dmr,
                    CONCAT('CX-', cx.idCaixa, ' / ', IFNULL(cx.documento, IFNULL(pcx.numeroDocumento, ''))) documento,
                    COALESCE(cx.historico, pcx.observacao) historico
                FROM caixa cx
                LEFT JOIN pagamentocaixa pcx ON pcx.idPagamentoCaixa = cx.idPagamentoCaixa
                LEFT JOIN banco b ON b.idBanco = cx.idBanco
                LEFT JOIN fornecedor ON fornecedor.idFornecedor = pcx.idFornecedor
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pcx.idCentroCusto
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = pcx.idPlanoContasCentroCusto
                LEFT JOIN planocontas plano ON plano.idPlanoContas = pccc.idPlanoContas
                WHERE cx.dataLancamento BETWEEN ? AND ?
                  AND UPPER(COALESCE(cx.tipoMovimento, '')) LIKE 'SA%%'
                  AND cx.idBanco IN (%s)
                """.formatted(CONTAS_CAIXA_DINHEIRO_IDS), (rs, rowNum) -> movimentoDespesa(rs, FinanceiroOrigemTipo.CAIXA_DINHEIRO,
                FinanceiroStatus.REALIZADO, FinanceiroRuleOrigins.CAIXA_DINHEIRO), Date.valueOf(filtro.inicio()),
                Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarExtratoAvulso(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    e.idExtrato origemId,
                    e.data dataCompetencia,
                    e.data dataVencimento,
                    e.data dataBaixa,
                    ABS(e.valor) valor,
                    e.idBanco bancoId,
                    b.conta banco,
                    NULL pessoaId,
                    COALESCE(e.conta, 'Lancamento bancario') pessoa,
                    pccc.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    NULL filialId,
                    NULL filial,
                    e.idPlanoContasCentroCusto planoContasCentroCustoId,
                    plano.descricao planoContas,
                    plano.classificacao classificacao,
                    plano.dmr dmr,
                    CONCAT('EXT-', e.idExtrato) documento,
                    e.historico historico
                FROM extrato e
                LEFT JOIN banco b ON b.idBanco = e.idBanco
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = e.idPlanoContasCentroCusto
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                LEFT JOIN planocontas plano ON plano.idPlanoContas = pccc.idPlanoContas
                WHERE e.data BETWEEN ? AND ?
                  AND UPPER(COALESCE(e.deb_credt, '')) LIKE 'D%%'
                  AND COALESCE(e.idNotaCompraDuplicata, 0) = 0
                  AND COALESCE(e.idFatura, 0) = 0
                  AND COALESCE(e.idCheque, 0) = 0
                  -- Exclui transferencias entre contas Salome (nao sao receita/despesa). TED/TRANSF/
                  -- TRANSFER pegam por substring; DOC/PIX/TEV usam fronteira de palavra (REGEXP) para
                  -- nao casar com termos como DOCUMENTO.
                  AND UPPER(COALESCE(e.historico, '')) NOT LIKE '%%TED%%'
                  AND UPPER(COALESCE(e.historico, '')) NOT LIKE '%%TRANSF%%'
                  AND UPPER(COALESCE(e.historico, '')) NOT LIKE '%%TRANSFER%%'
                  AND UPPER(COALESCE(e.historico, '')) NOT LIKE '%%SAQUE%%'
                  AND UPPER(COALESCE(e.historico, '')) NOT REGEXP '(^|[^A-Z])(DOC|PIX|TEV)([^A-Z]|$)'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM notacompraduplicatas ncd
                      WHERE ncd.idExtrato = e.idExtrato
                  )
                  AND (
                      COALESCE(e.idOperaca, 0) = 0
                      OR UPPER(COALESCE(e.historico, '')) LIKE '%%JURO%%'
                      OR UPPER(COALESCE(e.historico, '')) LIKE '%%TARIFA%%'
                      OR UPPER(COALESCE(e.historico, '')) LIKE '%%IOF%%'
                      OR UPPER(COALESCE(e.historico, '')) LIKE '%%DESPESA%%'
                  )
                  AND e.idBanco IN (%s)
                """.formatted(BANCOS_FLUXO_IDS), (rs, rowNum) -> movimentoDespesa(rs, FinanceiroOrigemTipo.EXTRATO_AVULSO,
                FinanceiroStatus.REALIZADO, FinanceiroRuleOrigins.EXTRATO_AVULSO), Date.valueOf(filtro.inicio()),
                Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarFaturasBaixadas(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    fb.idFatura origemId,
                    f.emissao dataCompetencia,
                    f.vencimento dataVencimento,
                    fb.dataBaixa dataBaixa,
                    fb.valor valor,
                    fb.idBanco bancoId,
                    b.conta banco,
                    f.idCliente pessoaId,
                    cliente.razaoSocial pessoa,
                    pccc.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    f.idFilial filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    f.idPlanoContasCentroCusto planoContasCentroCustoId,
                    plano.descricao planoContas,
                    plano.classificacao classificacao,
                    plano.dmr dmr,
                    CONCAT('FAT-', f.idFatura) documento,
                    CONCAT(IFNULL(fb.tipo, 'Baixa'), ' - ', IFNULL(fb.obs, '')) historico,
                    IF(fb.idBanco = ?
                        OR UPPER(COALESCE(b.conta, '')) LIKE '%%PERDAS%%DANOS%%'
                        OR UPPER(COALESCE(b.nome, '')) LIKE '%%PERDAS%%DANOS%%', 1, 0) bancoPerdasDanos,
                    IF(expresso.idCliente IS NULL, 0, 1) tomadorExpressoSalome
                FROM faturabaixa fb
                INNER JOIN fatura f ON f.idFatura = fb.idFatura
                LEFT JOIN banco b ON b.idBanco = fb.idBanco
                LEFT JOIN cliente ON cliente.idCliente = f.idCliente
                LEFT JOIN (
                    %s
                ) expresso ON expresso.idCliente = cliente.idCliente
                    OR (expresso.cnpjLimpo <> ''
                        AND expresso.cnpjLimpo = REPLACE(REPLACE(REPLACE(COALESCE(cliente.cnpj_cpf, ''), '.', ''), '/', ''), '-', ''))
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = f.idPlanoContasCentroCusto
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                LEFT JOIN planocontas plano ON plano.idPlanoContas = pccc.idPlanoContas
                LEFT JOIN filial fil ON fil.idFilial = f.idFilial
                WHERE fb.dataBaixa BETWEEN ? AND ?
                  AND UPPER(TRIM(COALESCE(fb.tipo, ''))) IN ('FATURA', 'JUROS')
                """.formatted(CLIENTE_EXPRESSO_SALOME_SQL), (rs, rowNum) -> movimentoReceita(rs,
                FinanceiroOrigemTipo.FATURA_BAIXA, FinanceiroStatus.REALIZADO, FinanceiroRuleOrigins.FATURA,
                rs.getInt("tomadorExpressoSalome") == 1, rs.getInt("bancoPerdasDanos") == 1),
                BANCO_PERDAS_DANOS_ID, Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarFaturasAbertas(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    f.idFatura origemId,
                    f.emissao dataCompetencia,
                    f.vencimento dataVencimento,
                    NULL dataBaixa,
                    (
                        SELECT COALESCE(SUM(c.valorTotal), 0)
                        FROM conhecimento c
                        WHERE c.idFatura = f.idFatura
                    ) valor,
                    IF(f.idBanco IN (%s), f.idBanco, NULL) bancoId,
                    IF(f.idBanco IN (%s), b.conta, NULL) banco,
                    f.idCliente pessoaId,
                    cliente.razaoSocial pessoa,
                    pccc.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    f.idFilial filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    f.idPlanoContasCentroCusto planoContasCentroCustoId,
                    plano.descricao planoContas,
                    plano.classificacao classificacao,
                    plano.dmr dmr,
                    CONCAT('FAT-', f.idFatura) documento,
                    f.obs historico,
                    IF(f.idBanco = ?
                        OR UPPER(COALESCE(b.conta, '')) LIKE '%%PERDAS%%DANOS%%'
                        OR UPPER(COALESCE(b.nome, '')) LIKE '%%PERDAS%%DANOS%%', 1, 0) bancoPerdasDanos,
                    IF(expresso.idCliente IS NULL, 0, 1) tomadorExpressoSalome
                FROM fatura f
                LEFT JOIN banco b ON b.idBanco = f.idBanco
                LEFT JOIN cliente ON cliente.idCliente = f.idCliente
                LEFT JOIN (
                    %s
                ) expresso ON expresso.idCliente = cliente.idCliente
                    OR (expresso.cnpjLimpo <> ''
                        AND expresso.cnpjLimpo = REPLACE(REPLACE(REPLACE(COALESCE(cliente.cnpj_cpf, ''), '.', ''), '/', ''), '-', ''))
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = f.idPlanoContasCentroCusto
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                LEFT JOIN planocontas plano ON plano.idPlanoContas = pccc.idPlanoContas
                LEFT JOIN filial fil ON fil.idFilial = f.idFilial
                WHERE f.vencimento BETWEEN ? AND ?
                  AND NOT EXISTS (SELECT 1 FROM faturabaixa fb WHERE fb.idFatura = f.idFatura)
                """.formatted(BANCOS_FLUXO_IDS, BANCOS_FLUXO_IDS, CLIENTE_EXPRESSO_SALOME_SQL), (rs, rowNum) -> movimentoReceita(rs,
                FinanceiroOrigemTipo.FATURA_ABERTA, FinanceiroStatus.PREVISTO, FinanceiroRuleOrigins.FATURA,
                rs.getInt("tomadorExpressoSalome") == 1, rs.getInt("bancoPerdasDanos") == 1),
                BANCO_PERDAS_DANOS_ID, Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()));
    }

    private List<FinanceiroMovimento> listarCtesAbertos(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    c.idConhecimento origemId,
                    c.cteEmissao dataCompetencia,
                    c.cteEmissao dataVencimentoBase,
                    NULL dataBaixa,
                    c.valorTotal valor,
                    NULL bancoId,
                    NULL banco,
                    tomador.idCliente pessoaId,
                    tomador.razaoSocial pessoa,
                    NULL centroCustoId,
                    NULL centroCusto,
                    c.idFilial filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    NULL planoContasCentroCustoId,
                    NULL planoContas,
                    NULL classificacao,
                    NULL dmr,
                    CONCAT('CT-e ', c.cte) documento,
                    CONCAT('CT-e emitido nao faturado - ', IFNULL(c.situacao, '')) historico,
                    IF(expresso.idCliente IS NULL, 0, 1) tomadorExpressoSalome
                FROM conhecimento c
                LEFT JOIN cliente clienteEmitente ON clienteEmitente.idCliente = c.idClienteEmitente
                LEFT JOIN cliente clienteDestinatario ON clienteDestinatario.idCliente = c.idClienteDestinatario
                LEFT JOIN cliente clienteConsignatario ON clienteConsignatario.idCliente = c.idClienteConsignatario
                LEFT JOIN cliente tomador ON tomador.idCliente = IF(UPPER(COALESCE(c.tipoPagamento, '')) LIKE 'DESTINAT%%'
                    AND UPPER(COALESCE(c.tipoPagamento, '')) LIKE '%%FOB%%',
                    c.idClienteDestinatario, IF(COALESCE(c.idClienteConsignatario, 0) > 0, c.idClienteConsignatario, c.idClienteEmitente))
                LEFT JOIN (
                    %s
                ) expresso ON expresso.idCliente = tomador.idCliente
                    OR (expresso.cnpjLimpo <> ''
                        AND expresso.cnpjLimpo = REPLACE(REPLACE(REPLACE(COALESCE(tomador.cnpj_cpf, ''), '.', ''), '/', ''), '-', ''))
                LEFT JOIN filial fil ON fil.idFilial = c.idFilial
                WHERE c.cteEmissao BETWEEN DATE_SUB(?, INTERVAL 45 DAY) AND ?
                  AND COALESCE(c.idFatura, 0) = 0
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%%'
                  AND UPPER(COALESCE(c.tipoPagamento, '')) NOT LIKE 'CORTES%%'
                """.formatted(CLIENTE_EXPRESSO_SALOME_SQL), (rs, rowNum) -> cteAberto(rs),
                Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()));
    }

    // ------------------------------------------------------------------------------------------------
    // Regime de competencia: receita por emissao do CT-e e despesa de nota de compra por dataEntrada
    // ------------------------------------------------------------------------------------------------

    /**
     * Receita de competencia: todos os CT-es (faturados ou nao) pela data de emissao
     * ({@code cteEmissao}), excluindo cancelados/inutilizados ({@code situacao}), cortesia
     * ({@code tipoFrete LIKE 'CORTES%'}) e CT-es cuja fatura esteja no banco 34/Perdas e Danos
     * (pela propria fatura {@code fatura.idBanco} ou por baixa em {@code faturabaixa}). O tomador
     * Expresso Salome e marcado para que o servico o remova como no caixa.
     */
    private List<FinanceiroMovimento> listarReceitasCteCompetencia(FinanceiroFiltro filtro) {
        return jdbcTemplate.query("""
                SELECT
                    c.idConhecimento origemId,
                    c.cteEmissao dataCompetencia,
                    c.valorTotal valor,
                    tomador.idCliente pessoaId,
                    tomador.razaoSocial pessoa,
                    c.idFilial filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    CONCAT('CT-e ', c.cte) documento,
                    CONCAT('CT-e emitido (competencia) - ', IFNULL(c.situacao, '')) historico,
                    IF(expresso.idCliente IS NULL, 0, 1) tomadorExpressoSalome,
                    -- Receita classificada como no caixa: plano da fatura; CT-e sem fatura usa a conta padrao 1.01.001 RECEITA BRUTA
                    COALESCE(pc.classificacao, '1.01.001') classificacao,
                    COALESCE(pc.descricao, 'RECEITA BRUTA') planoContas,
                    pc.dmr dmr,
                    f.idPlanoContasCentroCusto planoContasCentroCustoId
                FROM conhecimento c
                LEFT JOIN cliente tomador ON tomador.idCliente = IF(UPPER(COALESCE(c.tipoPagamento, '')) LIKE 'DESTINAT%%'
                    AND UPPER(COALESCE(c.tipoPagamento, '')) LIKE '%%FOB%%',
                    c.idClienteDestinatario, IF(COALESCE(c.idClienteConsignatario, 0) > 0, c.idClienteConsignatario, c.idClienteEmitente))
                LEFT JOIN (
                    %s
                ) expresso ON expresso.idCliente = tomador.idCliente
                    OR (expresso.cnpjLimpo <> ''
                        AND expresso.cnpjLimpo = REPLACE(REPLACE(REPLACE(COALESCE(tomador.cnpj_cpf, ''), '.', ''), '/', ''), '-', ''))
                LEFT JOIN filial fil ON fil.idFilial = c.idFilial
                LEFT JOIN fatura f ON f.idFatura = c.idFatura AND COALESCE(c.idFatura, 0) > 0
                LEFT JOIN banco bf ON bf.idBanco = f.idBanco
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = f.idPlanoContasCentroCusto
                LEFT JOIN planocontas pc ON pc.idPlanoContas = pccc.idPlanoContas
                WHERE c.cteEmissao BETWEEN ? AND ?
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%%'
                  -- Cortesia e marcada em tipoFrete (tipoPagamento nunca tem 'CORTES'); exclui CT-es cortesia
                  AND UPPER(COALESCE(c.tipoFrete, '')) NOT LIKE 'CORTES%%'
                  -- Perdas e Danos: exclui se a propria fatura esta no banco 34 (mesmo sem baixa)
                  AND COALESCE(f.idBanco, 0) <> ?
                  AND UPPER(COALESCE(bf.conta, '')) NOT LIKE '%%PERDAS%%DANOS%%'
                  AND UPPER(COALESCE(bf.nome, '')) NOT LIKE '%%PERDAS%%DANOS%%'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM faturabaixa fb
                      LEFT JOIN banco b ON b.idBanco = fb.idBanco
                      WHERE COALESCE(c.idFatura, 0) > 0
                        AND fb.idFatura = c.idFatura
                        AND (fb.idBanco = ?
                             OR UPPER(COALESCE(b.conta, '')) LIKE '%%PERDAS%%DANOS%%'
                             OR UPPER(COALESCE(b.nome, '')) LIKE '%%PERDAS%%DANOS%%')
                  )
                """.formatted(CLIENTE_EXPRESSO_SALOME_SQL), (rs, rowNum) -> cteEmitido(rs),
                Date.valueOf(filtro.inicio()), Date.valueOf(filtro.fim()), BANCO_PERDAS_DANOS_ID,
                BANCO_PERDAS_DANOS_ID);
    }

    /**
     * Despesa de competencia: nota de compra pela {@code dataEntrada} com rateio temporal
     * (valorNota/rateio por mes durante {@code rateio} meses), igual ao Relatorio de Despesas Entrada
     * legado. Exclui notas com {@code verRelatorioDespesas = Nao}. O valor da nota e dividido por
     * plano/centro de custo (notacomprarateio, com fallback em notacompraprodutos) e amortizado em
     * memoria, gerando um movimento por mes coberto dentro do periodo.
     */
    private List<FinanceiroMovimento> listarDespesasNotaCompraCompetencia(FinanceiroFiltro filtro) {
        List<NotaCompetenciaLinha> linhas = jdbcTemplate.query("""
                SELECT
                    nc.idNotaCompra origemId,
                    nc.dataEntrada dataEntrada,
                    GREATEST(COALESCE(nc.rateio, 1), 1) rateioMeses,
                    nc.valorNota * IFNULL(rateio.peso / NULLIF(rateio.totalPeso, 0), 1) valorSplit,
                    nc.idFornecedor pessoaId,
                    fornecedor.razaoSocial pessoa,
                    pccc.idCentroCusto centroCustoId,
                    cc.descricao centroCusto,
                    COALESCE(rateio.idFilial, nc.idFilial) filialId,
                    COALESCE(NULLIF(fil.sigla, ''), NULLIF(fil.descricao, '')) filial,
                    rateio.idPlanoContasCentroCusto planoContasCentroCustoId,
                    pc.descricao planoContas,
                    pc.classificacao classificacao,
                    pc.dmr dmr,
                    CONCAT('NC-', nc.idNotaCompra) documento,
                    CONCAT('Nota compra ', nc.notaFiscal, ' (competencia)') historico
                FROM notacompra nc
                LEFT JOIN fornecedor ON fornecedor.idFornecedor = nc.idFornecedor
                LEFT JOIN (
                    SELECT
                        nr.idNotaCompra,
                        nr.idFilial,
                        nr.idPlanoContasCentroCusto,
                        nr.valor peso,
                        (SELECT SUM(nr2.valor) FROM notacomprarateio nr2 WHERE nr2.idNotaCompra = nr.idNotaCompra) totalPeso
                    FROM notacomprarateio nr
                    WHERE COALESCE(nr.valor, 0) > 0
                    UNION ALL
                    SELECT
                        ncp.idNotaCompra,
                        ncp.idFilial,
                        ncp.idPlanoContasCentroCusto,
                        ncp.totalLiquido peso,
                        (SELECT SUM(ncp2.totalLiquido) FROM notacompraprodutos ncp2 WHERE ncp2.idNotaCompra = ncp.idNotaCompra) totalPeso
                    FROM notacompraprodutos ncp
                    WHERE COALESCE(ncp.totalLiquido, 0) > 0
                      AND NOT EXISTS (
                          SELECT 1
                          FROM notacomprarateio nr
                          WHERE nr.idNotaCompra = ncp.idNotaCompra
                            AND COALESCE(nr.valor, 0) > 0
                      )
                ) rateio ON rateio.idNotaCompra = nc.idNotaCompra
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = rateio.idPlanoContasCentroCusto
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                LEFT JOIN planocontas pc ON pc.idPlanoContas = pccc.idPlanoContas
                LEFT JOIN filial fil ON fil.idFilial = COALESCE(rateio.idFilial, nc.idFilial)
                WHERE nc.dataEntrada IS NOT NULL
                  AND nc.dataEntrada <= ?
                  AND DATE_ADD(nc.dataEntrada, INTERVAL GREATEST(COALESCE(nc.rateio, 1), 1) - 1 MONTH) >= ?
                  AND nc.dataCancelada IS NULL
                  AND UPPER(TRIM(COALESCE(nc.verRelatorioDespesas, ''))) NOT IN ('NAO', 'NÃO')
                """, (rs, rowNum) -> new NotaCompetenciaLinha(
                integerOrNull(rs, "origemId"), localDate(rs, "dataEntrada"), rs.getInt("rateioMeses"),
                decimal(rs, "valorSplit"), integerOrNull(rs, "pessoaId"), rs.getString("pessoa"),
                integerOrNull(rs, "centroCustoId"), rs.getString("centroCusto"), integerOrNull(rs, "filialId"),
                rs.getString("filial"), integerOrNull(rs, "planoContasCentroCustoId"), rs.getString("planoContas"),
                rs.getString("classificacao"), rs.getString("dmr"), rs.getString("documento"), rs.getString("historico")),
                Date.valueOf(filtro.fim()), Date.valueOf(filtro.inicio()));

        List<FinanceiroMovimento> movimentos = new ArrayList<>();
        for (NotaCompetenciaLinha linha : linhas) {
            movimentos.addAll(expandirRateioCompetencia(linha, filtro));
        }
        return movimentos;
    }

    /** Amortiza a linha da nota em um movimento por mes coberto dentro do periodo. */
    private List<FinanceiroMovimento> expandirRateioCompetencia(NotaCompetenciaLinha linha, FinanceiroFiltro filtro) {
        if (linha.dataEntrada() == null) {
            return List.of();
        }
        int meses = Math.max(linha.rateioMeses(), 1);
        BigDecimal valorMes = linha.valorSplit().divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);
        List<FinanceiroMovimento> movimentos = new ArrayList<>();
        for (int k = 0; k < meses; k++) {
            LocalDate mes = linha.dataEntrada().plusMonths(k).withDayOfMonth(1);
            if (mes.isBefore(filtro.inicio()) || mes.isAfter(filtro.fim())) {
                continue;
            }
            movimentos.add(new FinanceiroMovimento(FinanceiroNatureza.DESPESA, FinanceiroStatus.REALIZADO,
                    FinanceiroOrigemTipo.NOTA_COMPRA_COMPETENCIA, linha.origemId(), mes, mes, null, valorMes,
                    null, null, linha.pessoaId(), linha.pessoa(), linha.centroCustoId(), linha.centroCusto(),
                    linha.filialId(), linha.filial(), linha.planoContasCentroCustoId(), linha.planoContas(),
                    linha.classificacao(), linha.dmr(), linha.documento(), linha.historico(), false, false,
                    FinanceiroRuleOrigins.NOTA_COMPRA_COMPETENCIA));
        }
        return movimentos;
    }

    private FinanceiroMovimento cteEmitido(ResultSet rs) throws SQLException {
        LocalDate emissao = localDate(rs, "dataCompetencia");
        return new FinanceiroMovimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.REALIZADO,
                FinanceiroOrigemTipo.CTE_EMITIDO, integerOrNull(rs, "origemId"), emissao, emissao, null,
                decimal(rs, "valor"), null, null, integerOrNull(rs, "pessoaId"), rs.getString("pessoa"),
                null, null, integerOrNull(rs, "filialId"), rs.getString("filial"),
                integerOrNull(rs, "planoContasCentroCustoId"), rs.getString("planoContas"),
                rs.getString("classificacao"), rs.getString("dmr"),
                rs.getString("documento"), rs.getString("historico"), rs.getInt("tomadorExpressoSalome") == 1, false,
                FinanceiroRuleOrigins.CTE_EMITIDO);
    }

    /** Linha intermediaria de nota de compra antes da amortizacao por mes (regime competencia). */
    private record NotaCompetenciaLinha(Integer origemId, LocalDate dataEntrada, int rateioMeses, BigDecimal valorSplit,
            Integer pessoaId, String pessoa, Integer centroCustoId, String centroCusto, Integer filialId, String filial,
            Integer planoContasCentroCustoId, String planoContas, String classificacao, String dmr, String documento,
            String historico) {
    }

    @Override
    public BigDecimal somarToneladasTransportadas(FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        BigDecimal toneladas = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(nf.peso), 0) / 1000
                FROM conhecimento c
                LEFT JOIN (
                    SELECT idConhecimento, SUM(pesoNf) peso
                    FROM ConhecimentoNotasFiscais
                    GROUP BY idConhecimento
                ) nf ON nf.idConhecimento = c.idConhecimento
                WHERE c.cteEmissao BETWEEN ? AND ?
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%%'
                  AND UPPER(COALESCE(c.tipoPagamento, '')) NOT LIKE 'CORTES%%'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM faturabaixa fb
                      LEFT JOIN banco b ON b.idBanco = fb.idBanco
                      WHERE COALESCE(c.idFatura, 0) > 0
                        AND fb.idFatura = c.idFatura
                        AND (fb.idBanco = ?
                             OR UPPER(COALESCE(b.conta, '')) LIKE '%%PERDAS%%DANOS%%'
                             OR UPPER(COALESCE(b.nome, '')) LIKE '%%PERDAS%%DANOS%%')
                  )
                """.formatted(), BigDecimal.class,
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()), BANCO_PERDAS_DANOS_ID);
        return toneladas == null ? BigDecimal.ZERO : toneladas;
    }

    @Override
    public List<DreClienteDriver> listarDriversRateioPorCliente(FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT
                    tomador.idCliente idCliente,
                    tomador.razaoSocial nome,
                    COALESCE(SUM(nf.peso), 0) / 1000 toneladas,
                    COUNT(*) qtdCtes
                FROM conhecimento c
                LEFT JOIN cliente tomador ON tomador.idCliente = IF(UPPER(COALESCE(c.tipoPagamento, '')) LIKE 'DESTINAT%'
                    AND UPPER(COALESCE(c.tipoPagamento, '')) LIKE '%FOB%',
                    c.idClienteDestinatario, IF(COALESCE(c.idClienteConsignatario, 0) > 0, c.idClienteConsignatario, c.idClienteEmitente))
                LEFT JOIN (
                    SELECT idConhecimento, SUM(pesoNf) peso
                    FROM ConhecimentoNotasFiscais
                    GROUP BY idConhecimento
                ) nf ON nf.idConhecimento = c.idConhecimento
                WHERE c.cteEmissao BETWEEN ? AND ?
                  AND COALESCE(c.cte, 0) > 0
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'CANCEL%'
                  AND UPPER(COALESCE(c.situacao, '')) NOT LIKE 'INUTIL%'
                  AND UPPER(COALESCE(c.tipoPagamento, '')) NOT LIKE 'CORTES%'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM faturabaixa fb
                      LEFT JOIN banco b ON b.idBanco = fb.idBanco
                      WHERE COALESCE(c.idFatura, 0) > 0
                        AND fb.idFatura = c.idFatura
                        AND (fb.idBanco = ?
                             OR UPPER(COALESCE(b.conta, '')) LIKE '%PERDAS%DANOS%'
                             OR UPPER(COALESCE(b.nome, '')) LIKE '%PERDAS%DANOS%')
                  )
                GROUP BY tomador.idCliente, tomador.razaoSocial
                """, (rs, rowNum) -> new DreClienteDriver(integerOrNull(rs, "idCliente"),
                rs.getString("nome"), decimal(rs, "toneladas"), rs.getInt("qtdCtes")),
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()), BANCO_PERDAS_DANOS_ID);
    }

    @Override
    public List<FinanceiroDrillNode> listarClientes(FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT f.idCliente id, cli.razaoSocial nome, COUNT(*) qtd,
                    ROUND(SUM((SELECT COALESCE(SUM(c.valorTotal), 0) FROM conhecimento c WHERE c.idFatura = f.idFatura)), 2) total
                FROM fatura f
                LEFT JOIN cliente cli ON cli.idCliente = f.idCliente
                LEFT JOIN (
                    %s
                ) expresso ON expresso.idCliente = cli.idCliente
                    OR (expresso.cnpjLimpo <> ''
                        AND expresso.cnpjLimpo = REPLACE(REPLACE(REPLACE(COALESCE(cli.cnpj_cpf, ''), '.', ''), '/', ''), '-', ''))
                WHERE f.vencimento BETWEEN ? AND ?
                  AND expresso.idCliente IS NULL
                GROUP BY f.idCliente, cli.razaoSocial
                ORDER BY total DESC
                LIMIT 100
                """.formatted(CLIENTE_EXPRESSO_SALOME_SQL), (rs, n) -> new FinanceiroDrillNode(
                rs.getString("id"), nomeOuPadrao(rs.getString("nome"), "Cliente sem nome"),
                rs.getInt("qtd") + " fatura(s)", decimal(rs, "total"), rs.getInt("qtd"), true),
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
    }

    @Override
    public List<FinanceiroDrillNode> listarFaturasDoCliente(int idCliente, FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT f.idFatura id, f.vencimento venc,
                    (SELECT COALESCE(SUM(c.valorTotal), 0) FROM conhecimento c WHERE c.idFatura = f.idFatura) total,
                    (SELECT COUNT(*) FROM conhecimento c WHERE c.idFatura = f.idFatura) ctes,
                    (SELECT COUNT(*) FROM faturabaixa fb WHERE fb.idFatura = f.idFatura) baixas
                FROM fatura f
                WHERE f.idCliente = ? AND f.vencimento BETWEEN ? AND ?
                ORDER BY f.vencimento, f.idFatura
                LIMIT 500
                """, (rs, n) -> {
            int ctes = rs.getInt("ctes");
            boolean baixada = rs.getInt("baixas") > 0;
            String venc = rs.getString("venc");
            return new FinanceiroDrillNode("FAT" + rs.getString("id"), "FAT-" + rs.getString("id"),
                    "Venc " + textoData(venc) + (baixada ? " - baixada" : " - em aberto"),
                    decimal(rs, "total"), ctes, ctes > 0);
        }, idCliente, Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
    }

    @Override
    public List<FinanceiroDrillNode> listarCtesDaFatura(int idFatura) {
        return jdbcTemplate.query("""
                SELECT c.idConhecimento id, c.cte cte, c.cteEmissao emis, c.situacao sit, c.valorTotal total,
                    dest.razaoSocial destinatario
                FROM conhecimento c
                LEFT JOIN cliente dest ON dest.idCliente = c.idClienteDestinatario
                WHERE c.idFatura = ?
                ORDER BY c.cte
                LIMIT 2000
                """, (rs, n) -> new FinanceiroDrillNode("CTE" + rs.getString("id"), "CT-e " + rs.getString("cte"),
                nomeOuPadrao(rs.getString("destinatario"), "Destinatario") + " - " + textoData(rs.getString("emis")),
                decimal(rs, "total"), 0, false), idFatura);
    }

    @Override
    public List<DreFaturaCte> listarCtesDetalhadosDaFatura(int idFatura) {
        return jdbcTemplate.query("""
                SELECT
                    c.cte cte,
                    emit.razaoSocial remetente,
                    dest.razaoSocial destinatario,
                    COALESCE(nf.volumes, 0) volume,
                    COALESCE(nf.peso, 0) peso,
                    COALESCE(nf.valorNota, 0) valorNota,
                    c.valorTotal valorFrete
                FROM conhecimento c
                LEFT JOIN cliente emit ON emit.idCliente = c.idClienteEmitente
                LEFT JOIN cliente dest ON dest.idCliente = c.idClienteDestinatario
                LEFT JOIN (
                    SELECT idConhecimento,
                           SUM(quantidadeVolumes) volumes,
                           SUM(pesoNf) peso,
                           SUM(valorNF) valorNota
                    FROM ConhecimentoNotasFiscais
                    GROUP BY idConhecimento
                ) nf ON nf.idConhecimento = c.idConhecimento
                WHERE c.idFatura = ?
                ORDER BY c.cte
                LIMIT 2000
                """, (rs, n) -> new DreFaturaCte(integerOrNull(rs, "cte"),
                nomeOuPadrao(rs.getString("remetente"), "Remetente"),
                nomeOuPadrao(rs.getString("destinatario"), "Destinatario"),
                integerOrNull(rs, "volume"), decimal(rs, "peso"), decimal(rs, "valorNota"),
                decimal(rs, "valorFrete")), idFatura);
    }

    @Override
    public List<FinanceiroDrillNode> listarFornecedores(FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT nc.idFornecedor id, fo.razaoSocial nome, COUNT(*) qtd,
                    ROUND(SUM(IFNULL(NULLIF(ncd.valorpago, 0), ncd.valor)), 2) total
                FROM notacompraduplicatas ncd
                INNER JOIN notacompra nc ON nc.idNotaCompra = ncd.idNotaCompra
                LEFT JOIN fornecedor fo ON fo.idFornecedor = nc.idFornecedor
                WHERE (ncd.vencimento BETWEEN ? AND ? OR ncd.datapagamento BETWEEN ? AND ?)
                GROUP BY nc.idFornecedor, fo.razaoSocial
                ORDER BY total DESC
                LIMIT 100
                """, (rs, n) -> new FinanceiroDrillNode(rs.getString("id"),
                nomeOuPadrao(rs.getString("nome"), "Fornecedor sem nome"), rs.getInt("qtd") + " duplicata(s)",
                decimal(rs, "total"), rs.getInt("qtd"), true),
                Date.valueOf(f.inicio()), Date.valueOf(f.fim()), Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
    }

    @Override
    public List<FinanceiroDrillNode> listarNotasDoFornecedor(int idFornecedor, FinanceiroFiltro filtro) {
        FinanceiroFiltro f = filtro.normalizado();
        return jdbcTemplate.query("""
                SELECT nc.idNotaCompra id, nc.notaFiscal nf, nc.dataEntrada entrada,
                    ROUND(SUM(IFNULL(NULLIF(ncd.valorpago, 0), ncd.valor)), 2) total,
                    (SELECT COUNT(*) FROM notacompraprodutos p WHERE p.idNotaCompra = nc.idNotaCompra) prods
                FROM notacompraduplicatas ncd
                INNER JOIN notacompra nc ON nc.idNotaCompra = ncd.idNotaCompra
                WHERE nc.idFornecedor = ? AND (ncd.vencimento BETWEEN ? AND ? OR ncd.datapagamento BETWEEN ? AND ?)
                GROUP BY nc.idNotaCompra, nc.notaFiscal, nc.dataEntrada
                ORDER BY total DESC
                LIMIT 500
                """, (rs, n) -> {
            int prods = rs.getInt("prods");
            return new FinanceiroDrillNode("NC" + rs.getString("id"),
                    "NC-" + rs.getString("id") + " (NF " + nomeOuPadrao(rs.getString("nf"), "s/n") + ")",
                    "Entrada " + textoData(rs.getString("entrada")), decimal(rs, "total"), prods, prods > 0);
        }, idFornecedor, Date.valueOf(f.inicio()), Date.valueOf(f.fim()), Date.valueOf(f.inicio()), Date.valueOf(f.fim()));
    }

    @Override
    public List<FinanceiroDrillNode> listarProdutosDaNota(int idNotaCompra) {
        return jdbcTemplate.query("""
                SELECT p.idNotaCompraProdutos id, p.descricao descricao, p.quantidade qtd, p.totalLiquido total,
                    pc.descricao plano, cc.descricao centro
                FROM notacompraprodutos p
                LEFT JOIN planocontascentrocusto pccc ON pccc.idPlanoContasCentroCusto = p.idPlanoContasCentroCusto
                LEFT JOIN planocontas pc ON pc.idPlanoContas = pccc.idPlanoContas
                LEFT JOIN centrocusto cc ON cc.idCentroCusto = pccc.idCentroCusto
                WHERE p.idNotaCompra = ?
                ORDER BY p.totalLiquido DESC
                LIMIT 1000
                """, (rs, n) -> new FinanceiroDrillNode("PROD" + rs.getString("id"),
                nomeOuPadrao(rs.getString("descricao"), "Produto"),
                nomeOuPadrao(rs.getString("plano"), "Sem plano") + " / " + nomeOuPadrao(rs.getString("centro"), "Sem centro"),
                decimal(rs, "total"), 0, false), idNotaCompra);
    }

    private String nomeOuPadrao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private String textoData(String data) {
        if (data == null || data.length() < 10) {
            return data == null ? "-" : data;
        }
        return data.substring(8, 10) + "/" + data.substring(5, 7) + "/" + data.substring(0, 4);
    }

    private FinanceiroMovimento movimentoDespesa(ResultSet rs, FinanceiroOrigemTipo tipo, FinanceiroStatus status,
            LegacyOrigin origin) throws SQLException {
        return movimento(rs, FinanceiroNatureza.DESPESA, status, tipo, origin, false, false);
    }

    private FinanceiroMovimento movimentoReceita(ResultSet rs, FinanceiroOrigemTipo tipo, FinanceiroStatus status,
            LegacyOrigin origin, boolean tomadorExpressoSalome, boolean bancoPerdasDanos) throws SQLException {
        return movimento(rs, FinanceiroNatureza.RECEITA, status, tipo, origin, tomadorExpressoSalome, bancoPerdasDanos);
    }

    private FinanceiroMovimento cteAberto(ResultSet rs) throws SQLException {
        LocalDate emissao = localDate(rs, "dataCompetencia");
        LocalDate vencimento = vencimentoPadraoCte(emissao);
        return new FinanceiroMovimento(FinanceiroNatureza.RECEITA, FinanceiroStatus.PREVISTO,
                FinanceiroOrigemTipo.CTE_ABERTO, integerOrNull(rs, "origemId"), emissao, vencimento, null,
                decimal(rs, "valor"), integerOrNull(rs, "bancoId"), rs.getString("banco"), integerOrNull(rs, "pessoaId"),
                rs.getString("pessoa"), integerOrNull(rs, "centroCustoId"), rs.getString("centroCusto"),
                integerOrNull(rs, "filialId"), rs.getString("filial"),
                integerOrNull(rs, "planoContasCentroCustoId"), rs.getString("planoContas"),
                rs.getString("classificacao"), rs.getString("dmr"), rs.getString("documento"),
                rs.getString("historico"), rs.getInt("tomadorExpressoSalome") == 1, false,
                FinanceiroRuleOrigins.CTE_ABERTO);
    }

    private FinanceiroMovimento movimento(ResultSet rs, FinanceiroNatureza natureza, FinanceiroStatus status,
            FinanceiroOrigemTipo tipo, LegacyOrigin origin, boolean tomadorExpressoSalome, boolean bancoPerdasDanos)
            throws SQLException {
        return new FinanceiroMovimento(natureza, status, tipo, integerOrNull(rs, "origemId"),
                localDate(rs, "dataCompetencia"), localDate(rs, "dataVencimento"), localDate(rs, "dataBaixa"),
                decimal(rs, "valor"), integerOrNull(rs, "bancoId"), rs.getString("banco"),
                integerOrNull(rs, "pessoaId"), rs.getString("pessoa"), integerOrNull(rs, "centroCustoId"),
                rs.getString("centroCusto"), integerOrNull(rs, "filialId"), rs.getString("filial"),
                integerOrNull(rs, "planoContasCentroCustoId"),
                rs.getString("planoContas"), rs.getString("classificacao"), rs.getString("dmr"),
                rs.getString("documento"), rs.getString("historico"), tomadorExpressoSalome, bancoPerdasDanos, origin);
    }

    private LocalDate vencimentoPadraoCte(LocalDate emissao) {
        if (emissao == null) {
            return null;
        }
        if (emissao.getDayOfMonth() <= 15) {
            return emissao.withDayOfMonth(Math.min(30, emissao.lengthOfMonth()));
        }
        return emissao.plusMonths(1).withDayOfMonth(15);
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    /** Como {@link #decimal} porem preserva o sinal (saldos podem ser negativos). */
    private BigDecimal decimalComSinal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
