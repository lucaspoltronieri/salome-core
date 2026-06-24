package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.domain.torre.LocalArmazem;
import java.sql.PreparedStatement;
import java.sql.Statement;
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
public class TorreLocalRepository implements LocalArmazemRepository {

    private final JdbcTemplate jdbc;

    public TorreLocalRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<LocalArmazem> MAPPER = (rs, n) -> new LocalArmazem(
            rs.getLong("id"),
            rs.getInt("id_filial"),
            rs.getString("codigo"),
            rs.getString("nome"),
            rs.getString("tipo"),
            rs.getBoolean("ativo"));

    @Override
    public List<LocalArmazem> listarAtivos(int idFilial) {
        return jdbc.query("SELECT * FROM local_armazem WHERE id_filial = ? AND ativo = TRUE ORDER BY codigo",
                MAPPER, idFilial);
    }

    @Override
    public List<LocalArmazem> listarTodos(int idFilial) {
        return jdbc.query("SELECT * FROM local_armazem WHERE id_filial = ? ORDER BY ativo DESC, codigo",
                MAPPER, idFilial);
    }

    @Override
    public Optional<LocalArmazem> buscar(long id, int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM local_armazem WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public long criar(LocalArmazem local) {
        String sql = "INSERT INTO local_armazem (id_filial, codigo, nome, tipo, ativo) VALUES (?,?,?,?,TRUE)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, local.idFilial());
            ps.setString(2, local.codigo());
            ps.setString(3, local.nome());
            ps.setString(4, local.tipo());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public boolean definirAtivo(long id, int idFilial, boolean ativo) {
        return jdbc.update("UPDATE local_armazem SET ativo = ? WHERE id = ? AND id_filial = ?",
                ativo, id, idFilial) > 0;
    }
}
