package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.VeiculoRepository;
import br.com.salome.core.domain.torre.TipoVeiculo;
import br.com.salome.core.domain.torre.Veiculo;
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
public class TorreVeiculoRepository implements VeiculoRepository {

    private final JdbcTemplate jdbc;

    public TorreVeiculoRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<Veiculo> MAPPER = (rs, n) -> new Veiculo(
            rs.getLong("id"),
            rs.getInt("id_filial"),
            rs.getString("placa"),
            TipoVeiculo.porCodigo(rs.getString("tipo")).orElse(TipoVeiculo.ENTREGA),
            rs.getBoolean("ativo"));

    @Override
    public List<Veiculo> listar(int idFilial, TipoVeiculo tipo) {
        return jdbc.query(
                "SELECT * FROM veiculo WHERE id_filial = ? AND tipo = ? AND ativo = TRUE ORDER BY placa",
                MAPPER, idFilial, tipo.name());
    }

    @Override
    public Optional<Veiculo> buscarPorPlaca(int idFilial, String placa) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM veiculo WHERE id_filial = ? AND placa = ?", MAPPER, idFilial, placa));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public long criar(Veiculo veiculo) {
        String sql = "INSERT INTO veiculo (id_filial, placa, tipo, ativo) VALUES (?,?,?,TRUE)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, veiculo.idFilial());
            ps.setString(2, veiculo.placa());
            ps.setString(3, veiculo.tipo().name());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public boolean definirAtivo(long id, int idFilial, boolean ativo) {
        return jdbc.update("UPDATE veiculo SET ativo = ? WHERE id = ? AND id_filial = ?",
                ativo, id, idFilial) > 0;
    }
}
