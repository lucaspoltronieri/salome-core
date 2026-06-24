package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.FilialTorreRepository;
import br.com.salome.core.domain.torre.FilialTorre;
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
public class TorreFilialRepository implements FilialTorreRepository {

    private final JdbcTemplate jdbc;

    public TorreFilialRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<FilialTorre> MAPPER = (rs, n) -> new FilialTorre(
            rs.getInt("id_filial"),
            rs.getString("nome"),
            rs.getDate("data_corte_viagem").toLocalDate(),
            rs.getBoolean("ativa"));

    @Override
    public List<FilialTorre> listarAtivas() {
        return jdbc.query("SELECT * FROM filial_torre WHERE ativa = TRUE ORDER BY nome", MAPPER);
    }

    @Override
    public List<FilialTorre> listarTodas() {
        return jdbc.query("SELECT * FROM filial_torre ORDER BY ativa DESC, nome", MAPPER);
    }

    @Override
    public Optional<FilialTorre> buscar(int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM filial_torre WHERE id_filial = ?", MAPPER, idFilial));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void salvar(FilialTorre f) {
        jdbc.update("""
                INSERT INTO filial_torre (id_filial, nome, data_corte_viagem, ativa)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE nome = VALUES(nome),
                                        data_corte_viagem = VALUES(data_corte_viagem),
                                        ativa = VALUES(ativa)
                """, f.idFilial(), f.nome(), java.sql.Date.valueOf(f.dataCorteViagem()), f.ativa());
    }
}
