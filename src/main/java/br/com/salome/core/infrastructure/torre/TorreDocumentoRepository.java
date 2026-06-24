package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.DocumentoRepository;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.StatusDocumento;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreDocumentoRepository implements DocumentoRepository {

    private final JdbcTemplate jdbc;

    public TorreDocumentoRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private static final RowMapper<DocumentoOperacional> MAPPER = (rs, n) -> new DocumentoOperacional(
            rs.getLong("id"),
            rs.getInt("id_filial"),
            rs.getObject("numero_cte", Integer.class),
            rs.getObject("id_conhecimento_legado", Long.class),
            rs.getObject("id_viagem_legado", Long.class),
            rs.getBoolean("pre_cte"),
            rs.getObject("volumes", Integer.class),
            rs.getBigDecimal("peso"),
            rs.getString("remetente"),
            rs.getString("destinatario"),
            rs.getString("cidade_destino"),
            rs.getString("chave_nf"),
            StatusDocumento.valueOf(rs.getString("status")),
            rs.getObject("id_local_atual", Long.class),
            rs.getTimestamp("atualizado_em").toInstant());

    @Override
    public long salvar(DocumentoOperacional d) {
        Long existente = null;
        if (d.numeroCte() != null) {
            existente = jdbc.query(
                    "SELECT id FROM documento_operacional WHERE id_filial = ? AND numero_cte = ? LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null, d.idFilial(), d.numeroCte());
        }
        if (existente != null) {
            jdbc.update("""
                    UPDATE documento_operacional
                       SET id_conhecimento_legado = ?, id_viagem_legado = ?, pre_cte = ?, volumes = ?, peso = ?,
                           remetente = ?, destinatario = ?, cidade_destino = ?, chave_nf = ?, status = ?
                     WHERE id = ?
                    """,
                    d.idConhecimentoLegado(), d.idViagemLegado(), d.preCte(), d.volumes(), d.peso(),
                    d.remetente(), d.destinatario(), d.cidadeDestino(), d.chaveNf(), d.status().name(), existente);
            return existente;
        }
        String sql = """
                INSERT INTO documento_operacional
                  (id_filial, numero_cte, id_conhecimento_legado, id_viagem_legado, pre_cte, volumes, peso,
                   remetente, destinatario, cidade_destino, chave_nf, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, d.idFilial());
            setInt(ps, 2, d.numeroCte());
            setLong(ps, 3, d.idConhecimentoLegado());
            setLong(ps, 4, d.idViagemLegado());
            ps.setBoolean(5, d.preCte());
            setInt(ps, 6, d.volumes());
            if (d.peso() == null) ps.setNull(7, Types.DECIMAL); else ps.setBigDecimal(7, d.peso());
            ps.setString(8, d.remetente());
            ps.setString(9, d.destinatario());
            ps.setString(10, d.cidadeDestino());
            ps.setString(11, d.chaveNf());
            ps.setString(12, d.status().name());
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Override
    public int vincularAtividade(long idAtividade, long idDocumento, String papel,
                                 Integer volumes, BigDecimal peso, Long idUsuario, Instant em) {
        // Idempotente: re-bipar o mesmo CT-e na mesma atividade não duplica o vínculo.
        return jdbc.update("""
                INSERT INTO atividade_documento (id_atividade, id_documento, papel, volumes, peso, id_usuario, registrado_em)
                SELECT ?,?,?,?,?,?,? FROM dual
                 WHERE NOT EXISTS (
                       SELECT 1 FROM atividade_documento WHERE id_atividade = ? AND id_documento = ?)
                """, idAtividade, idDocumento, papel, volumes, peso, idUsuario, Timestamp.from(em),
                idAtividade, idDocumento);
    }

    @Override
    public void inserirMovimento(long idDocumento, String tipo, Long idAtividadeOrigem,
                                 Long idAtividadeDestino, Long idLocalOrigem, Long idLocalDestino,
                                 Long idUsuario, Instant em) {
        jdbc.update("""
                INSERT INTO movimento_documento
                  (id_documento, tipo, id_atividade_origem, id_atividade_destino, id_local_origem, id_local_destino, id_usuario, ocorrido_em)
                VALUES (?,?,?,?,?,?,?,?)
                """, idDocumento, tipo, idAtividadeOrigem, idAtividadeDestino, idLocalOrigem, idLocalDestino,
                idUsuario, Timestamp.from(em));
    }

    @Override
    public Optional<Long> ultimaAtividadeDescarga(long idDocumento) {
        return Optional.ofNullable(jdbc.query("""
                SELECT ad.id_atividade
                  FROM atividade_documento ad
                  JOIN atividade_armazem a ON a.id = ad.id_atividade
                 WHERE ad.id_documento = ?
                   AND a.tipo IN ('DESCARGA_TRANSFERENCIA','DESCARGA_COLETA')
                 ORDER BY ad.id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, idDocumento));
    }

    @Override
    public Optional<DocumentoOperacional> buscar(long id, int idFilial) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM documento_operacional WHERE id = ? AND id_filial = ?", MAPPER, id, idFilial));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<DocumentoOperacional> listarPorStatus(int idFilial, List<StatusDocumento> status) {
        if (status.isEmpty()) {
            return List.of();
        }
        String marcadores = String.join(",", status.stream().map(s -> "?").toList());
        Object[] args = new Object[status.size() + 1];
        args[0] = idFilial;
        for (int i = 0; i < status.size(); i++) {
            args[i + 1] = status.get(i).name();
        }
        return jdbc.query(
                "SELECT * FROM documento_operacional WHERE id_filial = ? AND status IN (" + marcadores + ") ORDER BY numero_cte",
                MAPPER, args);
    }

    @Override
    public void atualizarStatusELocal(long id, StatusDocumento status, Long idLocalAtual, Instant em) {
        jdbc.update("UPDATE documento_operacional SET status = ?, id_local_atual = ? WHERE id = ?",
                status.name(), idLocalAtual, id);
    }

    @Override
    public List<DocumentoOperacional> listarPorAtividade(long idAtividade) {
        return jdbc.query("""
                SELECT d.* FROM documento_operacional d
                  JOIN atividade_documento ad ON ad.id_documento = d.id
                 WHERE ad.id_atividade = ?
                 ORDER BY d.numero_cte
                """, MAPPER, idAtividade);
    }

    @Override
    public void inserirNf(long idDocumento, String chaveNf, String numeroNf, String serie, String cnpjEmitente) {
        jdbc.update("""
                INSERT INTO documento_nf (id_documento, chave_nf, numero_nf, serie_nf, cnpj_emitente)
                VALUES (?,?,?,?,?)
                """, idDocumento, chaveNf, numeroNf, serie, cnpjEmitente);
    }

    @Override
    public List<DocumentoOperacional> listarPreCtePendentes(int idFilial) {
        return jdbc.query(
                "SELECT * FROM documento_operacional WHERE id_filial = ? AND pre_cte = TRUE ORDER BY criado_em",
                MAPPER, idFilial);
    }

    @Override
    public void vincularCte(long idDocumento, int numeroCte, long idConhecimentoLegado, String remetente,
                            String destinatario, String cidadeDestino, Instant em) {
        jdbc.update("""
                UPDATE documento_operacional
                   SET numero_cte = ?, id_conhecimento_legado = ?, pre_cte = FALSE,
                       remetente = COALESCE(remetente, ?), destinatario = COALESCE(destinatario, ?),
                       cidade_destino = COALESCE(cidade_destino, ?)
                 WHERE id = ?
                """, numeroCte, idConhecimentoLegado, remetente, destinatario, cidadeDestino, idDocumento);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws java.sql.SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws java.sql.SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }
}
