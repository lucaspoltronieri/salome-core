package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.OcorrenciaRepository;
import br.com.salome.core.domain.torre.Ocorrencia;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
            rs.getObject("id_documento", Long.class),
            rs.getObject("id_atividade", Long.class),
            rs.getString("placa_veiculo"),
            rs.getString("descricao"),
            rs.getString("foto_path"),
            rs.getObject("id_usuario", Long.class),
            rs.getTimestamp("criado_em").toInstant());

    @Override
    public long inserir(Ocorrencia o) {
        String sql = """
                INSERT INTO ocorrencia_operacional
                  (id_filial, tipo, id_documento, id_atividade, placa_veiculo, descricao, foto_path, id_usuario)
                VALUES (?,?,?,?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, o.idFilial());
            ps.setString(2, o.tipo());
            setLong(ps, 3, o.idDocumento());
            setLong(ps, 4, o.idAtividade());
            ps.setString(5, o.placaVeiculo());
            ps.setString(6, o.descricao());
            ps.setString(7, o.fotoPath());
            setLong(ps, 8, o.idUsuario());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public java.util.Optional<Ocorrencia> buscar(long id, int idFilial) {
        try {
            return java.util.Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM ocorrencia_operacional WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public List<Ocorrencia> listarPorFilial(int idFilial, int limite) {
        return jdbc.query("SELECT * FROM ocorrencia_operacional WHERE id_filial = ? ORDER BY criado_em DESC LIMIT ?",
                MAPPER, idFilial, limite);
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws java.sql.SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }
}
