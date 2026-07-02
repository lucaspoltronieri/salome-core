package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.OcorrenciaRepository;
import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.OcorrenciaCte;
import br.com.salome.core.domain.torre.OcorrenciaDetalhe;
import br.com.salome.core.domain.torre.OcorrenciaFoto;
import br.com.salome.core.domain.torre.OcorrenciaItem;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreOcorrenciaRepository implements OcorrenciaRepository {

    private final JdbcTemplate jdbc;

    public TorreOcorrenciaRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<Ocorrencia> MAPPER = (rs, n) -> new Ocorrencia(
            rs.getLong("id"),
            rs.getInt("id_filial"),
            rs.getString("tipo"),
            rs.getString("status"),
            rs.getObject("id_documento", Long.class),
            rs.getObject("id_atividade", Long.class),
            rs.getString("placa_veiculo"),
            rs.getString("motorista"),
            rs.getObject("id_viagem_legado", Long.class),
            rs.getString("descricao"),
            rs.getString("culpa"),
            rs.getString("quem_causou"),
            rs.getString("responsavel_pagamento"),
            rs.getBigDecimal("valor_total"),
            instante(rs.getTimestamp("data_identificacao")),
            rs.getObject("duracao_segundos", Integer.class),
            rs.getString("foto_path"),
            rs.getObject("id_usuario", Long.class),
            rs.getTimestamp("criado_em").toInstant(),
            rs.getObject("tratada_por", Long.class),
            instante(rs.getTimestamp("tratada_em")),
            rs.getString("resolucao"));

    private static final RowMapper<OcorrenciaCte> CTE_MAPPER = (rs, n) -> new OcorrenciaCte(
            rs.getLong("id"),
            rs.getObject("numero_cte", Long.class),
            rs.getObject("id_documento", Long.class),
            rs.getString("remetente"),
            rs.getString("destinatario"));

    private static final RowMapper<OcorrenciaFoto> FOTO_MAPPER = (rs, n) -> new OcorrenciaFoto(
            rs.getLong("id"),
            rs.getString("categoria"),
            rs.getString("foto_path"),
            rs.getInt("ordem"));

    private static final RowMapper<OcorrenciaItem> ITEM_MAPPER = (rs, n) -> new OcorrenciaItem(
            rs.getLong("id"),
            rs.getString("codigo"),
            rs.getString("nome"),
            rs.getBigDecimal("quantidade"));

    @Override
    public long inserir(Ocorrencia o) {
        String sql = """
                INSERT INTO ocorrencia_operacional
                  (id_filial, tipo, status, id_documento, id_atividade, placa_veiculo, motorista,
                   id_viagem_legado, descricao, culpa, quem_causou, responsavel_pagamento, valor_total,
                   data_identificacao, duracao_segundos, foto_path, id_usuario)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, o.idFilial());
            ps.setString(2, o.tipo());
            ps.setString(3, o.status() == null ? "REGISTRADA" : o.status());
            setLong(ps, 4, o.idDocumento());
            setLong(ps, 5, o.idAtividade());
            ps.setString(6, o.placaVeiculo());
            ps.setString(7, o.motorista());
            setLong(ps, 8, o.idViagemLegado());
            ps.setString(9, o.descricao());
            ps.setString(10, o.culpa());
            ps.setString(11, o.quemCausou());
            ps.setString(12, o.responsavelPagamento());
            setBig(ps, 13, o.valorTotal());
            setTs(ps, 14, o.dataIdentificacao());
            setInt(ps, 15, o.duracaoSegundos());
            ps.setString(16, o.fotoPath());
            setLong(ps, 17, o.idUsuario());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public long inserirDetalhe(Ocorrencia cabecalho, List<OcorrenciaCte> ctes,
                               List<OcorrenciaFoto> fotos, List<OcorrenciaItem> itens) {
        long id = inserir(cabecalho);
        inserirCtes(id, ctes);
        inserirFotos(id, fotos);
        inserirItens(id, itens);
        return id;
    }

    private void inserirCtes(long idOcorrencia, List<OcorrenciaCte> ctes) {
        if (ctes == null || ctes.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO ocorrencia_cte (id_ocorrencia, numero_cte, id_documento, remetente, destinatario) VALUES (?,?,?,?,?)",
                ctes, ctes.size(), (ps, c) -> {
                    ps.setLong(1, idOcorrencia);
                    setLong(ps, 2, c.numeroCte());
                    setLong(ps, 3, c.idDocumento());
                    ps.setString(4, c.remetente());
                    ps.setString(5, c.destinatario());
                });
    }

    private void inserirFotos(long idOcorrencia, List<OcorrenciaFoto> fotos) {
        if (fotos == null || fotos.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO ocorrencia_foto (id_ocorrencia, categoria, foto_path, ordem) VALUES (?,?,?,?)",
                fotos, fotos.size(), (ps, f) -> {
                    ps.setLong(1, idOcorrencia);
                    ps.setString(2, f.categoria());
                    ps.setString(3, f.fotoPath());
                    ps.setInt(4, f.ordem());
                });
    }

    private void inserirItens(long idOcorrencia, List<OcorrenciaItem> itens) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO ocorrencia_item (id_ocorrencia, codigo, nome, quantidade) VALUES (?,?,?,?)",
                itens, itens.size(), (ps, it) -> {
                    ps.setLong(1, idOcorrencia);
                    ps.setString(2, it.codigo());
                    ps.setString(3, it.nome());
                    ps.setBigDecimal(4, it.quantidade() == null ? BigDecimal.ONE : it.quantidade());
                });
    }

    @Override
    public Optional<Ocorrencia> buscar(long id, int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM ocorrencia_operacional WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<OcorrenciaDetalhe> buscarDetalhe(long id, int idFilial) {
        Optional<Ocorrencia> cab = buscar(id, idFilial);
        if (cab.isEmpty()) {
            return Optional.empty();
        }
        List<OcorrenciaCte> ctes = jdbc.query(
                "SELECT * FROM ocorrencia_cte WHERE id_ocorrencia = ? ORDER BY id", CTE_MAPPER, id);
        List<OcorrenciaFoto> fotos = jdbc.query(
                "SELECT * FROM ocorrencia_foto WHERE id_ocorrencia = ? ORDER BY categoria, ordem, id", FOTO_MAPPER, id);
        List<OcorrenciaItem> itens = jdbc.query(
                "SELECT * FROM ocorrencia_item WHERE id_ocorrencia = ? ORDER BY id", ITEM_MAPPER, id);
        return Optional.of(new OcorrenciaDetalhe(cab.get(), ctes, fotos, itens));
    }

    @Override
    public List<Ocorrencia> listarPorFilial(int idFilial, int limite) {
        return listar(idFilial, null, null, limite);
    }

    @Override
    public List<Ocorrencia> listar(int idFilial, String status, String tipo, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ocorrencia_operacional WHERE id_filial = ?");
        List<Object> args = new ArrayList<>();
        args.add(idFilial);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND tipo = ?");
            args.add(tipo);
        }
        sql.append(" ORDER BY criado_em DESC LIMIT ?");
        args.add(limite);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public void atualizarTratamento(Ocorrencia o) {
        jdbc.update("""
                UPDATE ocorrencia_operacional
                   SET status = ?, culpa = ?, quem_causou = ?, responsavel_pagamento = ?,
                       valor_total = ?, resolucao = ?, tratada_por = ?, tratada_em = ?
                 WHERE id = ? AND id_filial = ?
                """,
                o.status(), o.culpa(), o.quemCausou(), o.responsavelPagamento(),
                o.valorTotal(), o.resolucao(), o.tratadaPor(),
                o.tratadaEm() == null ? null : Timestamp.from(o.tratadaEm()),
                o.id(), o.idFilial());
    }

    @Override
    public List<OcorrenciaFoto> fotos(long idOcorrencia) {
        return jdbc.query(
                "SELECT * FROM ocorrencia_foto WHERE id_ocorrencia = ? ORDER BY categoria, ordem, id",
                FOTO_MAPPER, idOcorrencia);
    }

    private static Instant instante(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    private static void setBig(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DECIMAL); else ps.setBigDecimal(i, v);
    }

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setTimestamp(i, Timestamp.from(v));
    }
}
