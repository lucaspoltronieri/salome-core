package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.AuditoriaRepository;
import br.com.salome.core.domain.torre.EventoAuditoria;
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
public class TorreAuditoriaRepository implements AuditoriaRepository {

    private final JdbcTemplate jdbc;

    public TorreAuditoriaRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<EventoAuditoria> MAPPER = (rs, n) -> new EventoAuditoria(
            rs.getLong("id"),
            rs.getObject("id_filial", Integer.class),
            rs.getObject("id_usuario", Long.class),
            rs.getString("acao"),
            rs.getString("entidade"),
            rs.getObject("id_entidade", Long.class),
            rs.getString("detalhe"),
            rs.getTimestamp("ocorrido_em").toInstant());

    @Override
    public long registrar(EventoAuditoria e) {
        String sql = """
                INSERT INTO evento_auditoria
                  (id_filial, id_usuario, acao, entidade, id_entidade, detalhe, ocorrido_em)
                VALUES (?,?,?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setInt(ps, 1, e.idFilial());
            setLong(ps, 2, e.idUsuario());
            ps.setString(3, e.acao());
            ps.setString(4, e.entidade());
            setLong(ps, 5, e.idEntidade());
            ps.setString(6, e.detalhe());
            ps.setTimestamp(7, Timestamp.from(e.ocorridoEm()));
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public List<EventoAuditoria> listarPorFilial(int idFilial, int limite) {
        return jdbc.query(
                "SELECT * FROM evento_auditoria WHERE id_filial = ? ORDER BY ocorrido_em DESC, id DESC LIMIT ?",
                MAPPER, idFilial, limite);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws java.sql.SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws java.sql.SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }
}
