package br.com.salome.core.infrastructure.torre.auth;

import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreUsuarioRepository implements UsuarioRepository {

    private static final String SQL = """
            SELECT u.id, u.nome, u.login, u.senha_hash, u.id_filial, p.codigo AS perfil, u.ativo
              FROM usuario u
              JOIN perfil p ON p.id = u.id_perfil
             WHERE u.login = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TorreUsuarioRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbcTemplate = torreJdbcTemplate;
    }

    @Override
    public Optional<UsuarioCredencial> buscarPorLogin(String login) {
        try {
            UsuarioCredencial credencial = jdbcTemplate.queryForObject(SQL, (rs, rowNum) -> new UsuarioCredencial(
                    rs.getLong("id"),
                    rs.getString("nome"),
                    rs.getString("login"),
                    rs.getString("senha_hash"),
                    rs.getInt("id_filial"),
                    PerfilCodigo.valueOf(rs.getString("perfil")),
                    rs.getBoolean("ativo")
            ), login);
            return Optional.ofNullable(credencial);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
