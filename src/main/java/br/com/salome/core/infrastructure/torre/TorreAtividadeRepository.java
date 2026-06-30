package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.AtividadeRepository;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.CaminhaoEmDescarga;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.TipoAtividade;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class TorreAtividadeRepository implements AtividadeRepository {

    private final JdbcTemplate jdbc;

    public TorreAtividadeRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<Atividade> MAPPER = (rs, n) -> new Atividade(
            rs.getLong("id"),
            rs.getInt("id_filial"),
            TipoAtividade.valueOf(rs.getString("tipo")),
            rs.getString("subtipo"),
            StatusAtividade.valueOf(rs.getString("status")),
            rs.getObject("id_viagem_legado", Long.class),
            rs.getString("placa_veiculo"),
            rs.getObject("id_responsavel", Long.class),
            rs.getString("observacao"),
            instante(rs.getTimestamp("iniciada_em")),
            instante(rs.getTimestamp("finalizada_em")));

    @Override
    public long inserir(Atividade a) {
        String sql = """
                INSERT INTO atividade_armazem
                  (id_filial, tipo, subtipo, status, id_viagem_legado, placa_veiculo, id_responsavel, observacao, iniciada_em)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, a.idFilial());
            ps.setString(2, a.tipo().name());
            ps.setString(3, a.subtipo());
            ps.setString(4, a.status().name());
            ps.setObject(5, a.idViagemLegado());
            ps.setString(6, a.placaVeiculo());
            ps.setObject(7, a.idResponsavel());
            ps.setString(8, a.observacao());
            ps.setTimestamp(9, Timestamp.from(a.iniciadaEm()));
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public Optional<Atividade> buscar(long id, int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM atividade_armazem WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Atividade> listarAbertas(int idFilial) {
        return jdbc.query(
                "SELECT * FROM atividade_armazem WHERE id_filial = ? AND status = 'ABERTA' ORDER BY iniciada_em",
                MAPPER, idFilial);
    }

    @Override
    public void finalizar(long id, Instant finalizadaEm) {
        jdbc.update("UPDATE atividade_armazem SET status = 'FINALIZADA', finalizada_em = ? WHERE id = ?",
                Timestamp.from(finalizadaEm), id);
    }

    @Override
    public void cancelar(long id, Instant canceladaEm, String motivo) {
        jdbc.update("""
                UPDATE atividade_armazem
                   SET status = 'CANCELADA', finalizada_em = ?, observacao = ?
                 WHERE id = ?
                """, Timestamp.from(canceladaEm), motivo, id);
    }

    @Override
    public Set<Long> idsViagensComDescarga(int idFilial) {
        // Só esconde o caminhão se a descarga estiver viva (ABERTA/FINALIZADA).
        // Uma descarga CANCELADA libera a viagem pra reaparecer em "aguardando".
        List<Long> ids = jdbc.queryForList("""
                SELECT DISTINCT id_viagem_legado
                  FROM atividade_armazem
                 WHERE id_filial = ?
                   AND tipo = 'DESCARGA_TRANSFERENCIA'
                   AND id_viagem_legado IS NOT NULL
                   AND status <> 'CANCELADA'
                """, Long.class, idFilial);
        return new HashSet<>(ids);
    }

    @Override
    public List<CaminhaoEmDescarga> listarCaminhoesEmDescarga(int idFilial, Instant finalizadaDesde) {
        return jdbc.query("""
                SELECT a.id_viagem_legado AS id_viagem,
                       MAX(a.placa_veiculo) AS placa,
                       MAX(a.status = 'ABERTA') AS descarga_aberta
                  FROM atividade_armazem a
                 WHERE a.id_filial = ?
                   AND a.tipo = 'DESCARGA_TRANSFERENCIA'
                   AND a.id_viagem_legado IS NOT NULL
                   AND ( a.status = 'ABERTA'
                         OR (a.status = 'FINALIZADA' AND a.finalizada_em >= ?) )
                 GROUP BY a.id_viagem_legado
                 ORDER BY descarga_aberta DESC, placa
                """,
                (rs, n) -> new CaminhaoEmDescarga(
                        rs.getObject("id_viagem", Long.class),
                        rs.getString("placa"),
                        rs.getBoolean("descarga_aberta")),
                idFilial, Timestamp.from(finalizadaDesde));
    }

    private static Instant instante(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
