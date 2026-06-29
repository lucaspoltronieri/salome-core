package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.RelatorioRepository;
import br.com.salome.core.domain.torre.RelatorioOperadores;
import br.com.salome.core.domain.torre.RelatorioOperadores.AtividadeOperador;
import br.com.salome.core.domain.torre.RelatorioOperadores.CteOperador;
import br.com.salome.core.domain.torre.RelatorioOperadores.Operador;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Monta o relatório "o que cada operador fez" em duas consultas (participações e
 * CT-es marcados) e junta em memória por operador → atividade → CT-es. O tempo por
 * CT-e é o intervalo entre marcações do mesmo operador na mesma atividade (LAG).
 */
@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreRelatorioRepository implements RelatorioRepository {

    private final JdbcTemplate jdbc;

    public TorreRelatorioRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    private record ParticipRow(long idUsuario, String nome, long idAtividade, String tipo, String subtipo,
                               String placa, String status, Instant entrada, Instant saida, long segundos) {
    }

    private record CteRow(long idUsuario, String nome, long idAtividade, String tipo, String subtipo,
                          String placa, String status, CteOperador cte) {
    }

    private static final RowMapper<ParticipRow> PARTICIP = (rs, n) -> new ParticipRow(
            rs.getLong("id_usuario"), rs.getString("nome"), rs.getLong("id_atividade"),
            rs.getString("tipo"), rs.getString("subtipo"), rs.getString("placa_veiculo"), rs.getString("status"),
            instante(rs.getTimestamp("entrada_em")), instante(rs.getTimestamp("saida_em")), rs.getLong("segundos"));

    private static final RowMapper<CteRow> CTE = (rs, n) -> new CteRow(
            rs.getLong("id_usuario"), rs.getString("nome"), rs.getLong("id_atividade"),
            rs.getString("tipo"), rs.getString("subtipo"), rs.getString("placa_veiculo"), rs.getString("status"),
            new CteOperador(
                    rs.getObject("numero_cte", Integer.class),
                    rs.getObject("volumes", Integer.class),
                    rs.getBigDecimal("peso"),
                    rs.getString("remetente"),
                    rs.getString("destinatario"),
                    rs.getString("cidade_destino"),
                    rs.getString("doc_status"),
                    instante(rs.getTimestamp("registrado_em")),
                    rs.getObject("seg_no_cte", Long.class)));

    @Override
    public RelatorioOperadores operadores(int idFilial, LocalDate de, LocalDate ate) {
        Map<Long, OpAcc> ops = new LinkedHashMap<>();

        // 1) Participações (tempo por pessoa/atividade), somando segmentos (ex.: pós-almoço).
        List<ParticipRow> parts = jdbc.query("""
                SELECT p.id_usuario, u.nome, a.id AS id_atividade, a.tipo, a.subtipo,
                       a.placa_veiculo, a.status, p.entrada_em, p.saida_em,
                       TIMESTAMPDIFF(SECOND, p.entrada_em, COALESCE(p.saida_em, NOW())) AS segundos
                  FROM atividade_participante p
                  JOIN usuario u ON u.id = p.id_usuario
                  JOIN atividade_armazem a ON a.id = p.id_atividade
                 WHERE a.id_filial = ? AND DATE(p.entrada_em) BETWEEN ? AND ?
                 ORDER BY u.nome, p.entrada_em
                """, PARTICIP, idFilial, de, ate);
        for (ParticipRow r : parts) {
            atv(ops, r.idUsuario(), r.nome(), r.idAtividade(), r.tipo(), r.subtipo(), r.placa(), r.status())
                    .somarSegmento(r.entrada(), r.saida(), r.segundos());
        }

        // 2) CT-es marcados por cada operador, com o tempo aproximado por CT-e (LAG).
        List<CteRow> ctes = jdbc.query("""
                SELECT ad.id_usuario, u.nome, ad.id_atividade, a.tipo, a.subtipo, a.placa_veiculo, a.status,
                       d.numero_cte, d.volumes, d.peso, d.remetente, d.destinatario, d.cidade_destino,
                       d.status AS doc_status, ad.registrado_em,
                       TIMESTAMPDIFF(SECOND,
                           LAG(ad.registrado_em) OVER (PARTITION BY ad.id_usuario, ad.id_atividade ORDER BY ad.registrado_em),
                           ad.registrado_em) AS seg_no_cte
                  FROM atividade_documento ad
                  JOIN usuario u ON u.id = ad.id_usuario
                  JOIN documento_operacional d ON d.id = ad.id_documento
                  JOIN atividade_armazem a ON a.id = ad.id_atividade
                 WHERE a.id_filial = ? AND DATE(ad.registrado_em) BETWEEN ? AND ?
                 ORDER BY u.nome, ad.id_atividade, ad.registrado_em
                """, CTE, idFilial, de, ate);
        for (CteRow r : ctes) {
            atv(ops, r.idUsuario(), r.nome(), r.idAtividade(), r.tipo(), r.subtipo(), r.placa(), r.status())
                    .ctes.add(r.cte());
        }

        List<Operador> operadores = new ArrayList<>();
        for (OpAcc op : ops.values()) {
            operadores.add(op.build());
        }
        return new RelatorioOperadores(de, ate, operadores);
    }

    private static AtvAcc atv(Map<Long, OpAcc> ops, long idUsuario, String nome, long idAtividade,
                              String tipo, String subtipo, String placa, String status) {
        OpAcc op = ops.computeIfAbsent(idUsuario, k -> new OpAcc(idUsuario, nome));
        return op.atividades.computeIfAbsent(idAtividade,
                k -> new AtvAcc(idAtividade, tipo, subtipo, placa, status));
    }

    private static Instant instante(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    // ---- Acumuladores mutáveis (montagem em memória) --------------------

    private static final class OpAcc {
        final long idUsuario;
        final String nome;
        final Map<Long, AtvAcc> atividades = new LinkedHashMap<>();

        OpAcc(long idUsuario, String nome) {
            this.idUsuario = idUsuario;
            this.nome = nome;
        }

        Operador build() {
            List<AtividadeOperador> atvs = new ArrayList<>();
            long totalSeg = 0;
            int totalCtes = 0;
            long totalVol = 0;
            BigDecimal totalPeso = BigDecimal.ZERO;
            for (AtvAcc a : atividades.values()) {
                atvs.add(a.build());
                totalSeg += a.segundos;
                for (CteOperador c : a.ctes) {
                    totalCtes++;
                    if (c.volumes() != null) {
                        totalVol += c.volumes();
                    }
                    if (c.peso() != null) {
                        totalPeso = totalPeso.add(c.peso());
                    }
                }
            }
            return new Operador(idUsuario, nome, atividades.size(), totalSeg, totalCtes, totalVol, totalPeso, atvs);
        }
    }

    private static final class AtvAcc {
        final long idAtividade;
        final String tipo;
        final String subtipo;
        final String placa;
        final String status;
        Instant entrada;
        Instant saida;
        boolean algumAberto;
        long segundos;
        final List<CteOperador> ctes = new ArrayList<>();

        AtvAcc(long idAtividade, String tipo, String subtipo, String placa, String status) {
            this.idAtividade = idAtividade;
            this.tipo = tipo;
            this.subtipo = subtipo;
            this.placa = placa;
            this.status = status;
        }

        void somarSegmento(Instant ent, Instant sai, long seg) {
            segundos += seg;
            if (ent != null && (entrada == null || ent.isBefore(entrada))) {
                entrada = ent;
            }
            if (sai == null) {
                algumAberto = true;
            } else if (saida == null || sai.isAfter(saida)) {
                saida = sai;
            }
        }

        AtividadeOperador build() {
            return new AtividadeOperador(idAtividade, tipo, subtipo, placa, status,
                    entrada, algumAberto ? null : saida, segundos, ctes);
        }
    }
}
