package br.com.salome.core.infrastructure.torre.auth;

import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
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

    private static final RowMapper<UsuarioResumo> RESUMO = (rs, n) -> new UsuarioResumo(
            rs.getLong("id"),
            rs.getString("nome"),
            rs.getString("login"),
            rs.getInt("id_filial"),
            PerfilCodigo.valueOf(rs.getString("perfil")),
            rs.getBoolean("ativo"));

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

    @Override
    public long criar(String login, String nome, String senhaHash, int idFilial, PerfilCodigo perfil) {
        String sql = """
                INSERT INTO usuario (login, nome, senha_hash, id_filial, id_perfil, ativo)
                VALUES (?, ?, ?, ?, (SELECT id FROM perfil WHERE codigo = ?), TRUE)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, login);
            ps.setString(2, nome);
            ps.setString(3, senhaHash);
            ps.setInt(4, idFilial);
            ps.setString(5, perfil.name());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public List<UsuarioResumo> listar(int idFilial) {
        return jdbcTemplate.query("""
                SELECT u.id, u.nome, u.login, u.id_filial, p.codigo AS perfil, u.ativo
                  FROM usuario u
                  JOIN perfil p ON p.id = u.id_perfil
                 WHERE u.id_filial = ?
                 ORDER BY u.nome
                """, RESUMO, idFilial);
    }

    @Override
    public Optional<UsuarioResumo> buscar(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT u.id, u.nome, u.login, u.id_filial, p.codigo AS perfil, u.ativo
                      FROM usuario u
                      JOIN perfil p ON p.id = u.id_perfil
                     WHERE u.id = ?
                    """, RESUMO, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean definirAtivo(long id, boolean ativo) {
        return jdbcTemplate.update("UPDATE usuario SET ativo = ? WHERE id = ?", ativo, id) > 0;
    }
}
