package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.ParticipanteRepository;
import br.com.salome.core.domain.torre.Participante;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
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
public class TorreParticipanteRepository implements ParticipanteRepository {

    private final JdbcTemplate jdbc;

    public TorreParticipanteRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<Participante> MAPPER = (rs, n) -> new Participante(
            rs.getLong("id"),
            rs.getLong("id_atividade"),
            rs.getLong("id_usuario"),
            rs.getString("nome_usuario"),
            rs.getString("funcao"),
            rs.getTimestamp("entrada_em").toInstant(),
            rs.getTimestamp("saida_em") == null ? null : rs.getTimestamp("saida_em").toInstant(),
            rs.getString("dispositivo"),
            rs.getString("origem"));

    @Override
    public int encerrarAtivasDoUsuario(long idUsuario, Instant em) {
        return jdbc.update(
                "UPDATE atividade_participante SET saida_em = ? WHERE id_usuario = ? AND saida_em IS NULL",
                Timestamp.from(em), idUsuario);
    }

    @Override
    public long abrir(long idAtividade, long idUsuario, String funcao, String origem, Instant em) {
        String sql = """
                INSERT INTO atividade_participante (id_atividade, id_usuario, funcao, entrada_em, origem)
                VALUES (?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, idAtividade);
            ps.setLong(2, idUsuario);
            ps.setString(3, funcao);
            ps.setTimestamp(4, Timestamp.from(em));
            ps.setString(5, origem);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public int encerrarNaAtividade(long idAtividade, long idUsuario, Instant em) {
        return jdbc.update(
                "UPDATE atividade_participante SET saida_em = ? WHERE id_atividade = ? AND id_usuario = ? AND saida_em IS NULL",
                Timestamp.from(em), idAtividade, idUsuario);
    }

    @Override
    public int encerrarTodasDaAtividade(long idAtividade, Instant em) {
        return jdbc.update(
                "UPDATE atividade_participante SET saida_em = ? WHERE id_atividade = ? AND saida_em IS NULL",
                Timestamp.from(em), idAtividade);
    }

    @Override
    public List<Participante> listarPorAtividade(long idAtividade) {
        return jdbc.query("""
                SELECT p.id, p.id_atividade, p.id_usuario, u.nome AS nome_usuario, p.funcao,
                       p.entrada_em, p.saida_em, p.dispositivo, p.origem
                  FROM atividade_participante p
                  JOIN usuario u ON u.id = p.id_usuario
                 WHERE p.id_atividade = ?
                 ORDER BY p.entrada_em
                """, MAPPER, idAtividade);
    }
}
