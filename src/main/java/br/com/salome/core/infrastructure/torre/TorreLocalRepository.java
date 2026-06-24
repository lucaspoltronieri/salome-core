package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.domain.torre.LocalArmazem;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
    public Optional<LocalArmazem> buscar(long id, int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM local_armazem WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
