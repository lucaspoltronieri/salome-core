package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.RelatorioRepository;
import br.com.salome.core.application.torre.ViagemLegadoRepository;
import br.com.salome.core.domain.torre.Intervalos;
import br.com.salome.core.domain.torre.Intervalos.Intervalo;
import br.com.salome.core.domain.torre.RelatorioOperadores;
import br.com.salome.core.domain.torre.RelatorioOperadores.AtividadeOperador;
import br.com.salome.core.domain.torre.RelatorioOperadores.CteOperador;
import br.com.salome.core.domain.torre.RelatorioOperadores.Operador;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.AtividadeCarga;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.Carga;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.ParticipanteCarga;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas.Totais;
import br.com.salome.core.domain.torre.ResumoViagemLegado;
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
    private final ViagemLegadoRepository viagemLegadoRepository;

    public TorreRelatorioRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate,
                                    ViagemLegadoRepository viagemLegadoRepository) {
        this.jdbc = torreJdbcTemplate;
        this.viagemLegadoRepository = viagemLegadoRepository;
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

    // ==== Produtividade por carga ==========================================

    private static final String TIPOS_DESCARGA = "('DESCARGA_TRANSFERENCIA','DESCARGA_COLETA')";

    private record CargaBaseRow(long idViagem, String placa, String tipo, Instant inicio, Instant fim,
                                int qtdCtes, long volumes, BigDecimal peso) {
    }

    private record PartCargaRow(long idViagem, long idAtividade, String status, Instant iniciadaEm,
                                Instant finalizadaEm, long idUsuario, String nome, Instant entrada,
                                Instant saida, long segundos) {
    }

    @Override
    public RelatorioProdutividadeCargas produtividadeCargas(int idFilial, LocalDate de, LocalDate ate) {
        // 1) Base por carga (agrega todas as atividades de descarga da mesma viagem).
        List<CargaBaseRow> bases = jdbc.query("""
                SELECT a.id_viagem_legado                 AS id_viagem,
                       MAX(a.placa_veiculo)               AS placa,
                       MIN(a.tipo)                        AS tipo,
                       MIN(a.iniciada_em)                 AS inicio,
                       MAX(a.finalizada_em)               AS fim,
                       COUNT(DISTINCT ad.id_documento)    AS qtd_ctes,
                       COALESCE(SUM(ad.volumes), 0)       AS volumes,
                       COALESCE(SUM(ad.peso), 0)          AS peso
                  FROM atividade_armazem a
                  LEFT JOIN atividade_documento ad ON ad.id_atividade = a.id
                 WHERE a.id_filial = ?
                   AND a.tipo IN """ + TIPOS_DESCARGA + """
                   AND a.status = 'FINALIZADA'
                   AND a.id_viagem_legado IS NOT NULL
                   AND DATE(a.finalizada_em) BETWEEN ? AND ?
                 GROUP BY a.id_viagem_legado
                 ORDER BY MAX(a.finalizada_em) DESC
                """, (rs, n) -> new CargaBaseRow(
                        rs.getLong("id_viagem"),
                        rs.getString("placa"),
                        rs.getString("tipo"),
                        instante(rs.getTimestamp("inicio")),
                        instante(rs.getTimestamp("fim")),
                        rs.getInt("qtd_ctes"),
                        rs.getLong("volumes"),
                        rs.getBigDecimal("peso")),
                idFilial, de, ate);

        Map<Long, CargaAcc> cargas = new LinkedHashMap<>();
        for (CargaBaseRow b : bases) {
            cargas.put(b.idViagem(), new CargaAcc(b));
        }
        if (cargas.isEmpty()) {
            return new RelatorioProdutividadeCargas(de, ate,
                    new Totais(0, 0, 0L, BigDecimal.ZERO, 0L, 0L, 0L), List.of());
        }

        // 2) Participações das atividades dessas cargas (tempos + drill-down por pessoa).
        List<PartCargaRow> parts = jdbc.query("""
                SELECT a.id_viagem_legado AS id_viagem, a.id AS id_atividade, a.status AS status,
                       a.iniciada_em AS iniciada_em, a.finalizada_em AS finalizada_em,
                       p.id_usuario AS id_usuario, u.nome AS nome, p.entrada_em AS entrada_em, p.saida_em AS saida_em,
                       TIMESTAMPDIFF(SECOND, p.entrada_em, COALESCE(p.saida_em, a.finalizada_em, NOW())) AS segundos
                  FROM atividade_participante p
                  JOIN usuario u ON u.id = p.id_usuario
                  JOIN atividade_armazem a ON a.id = p.id_atividade
                 WHERE a.id_filial = ?
                   AND a.tipo IN """ + TIPOS_DESCARGA + """
                   AND a.status = 'FINALIZADA'
                   AND a.id_viagem_legado IS NOT NULL
                   AND DATE(a.finalizada_em) BETWEEN ? AND ?
                 ORDER BY a.id_viagem_legado, a.id, p.entrada_em
                """, (rs, n) -> new PartCargaRow(
                        rs.getLong("id_viagem"),
                        rs.getLong("id_atividade"),
                        rs.getString("status"),
                        instante(rs.getTimestamp("iniciada_em")),
                        instante(rs.getTimestamp("finalizada_em")),
                        rs.getLong("id_usuario"),
                        rs.getString("nome"),
                        instante(rs.getTimestamp("entrada_em")),
                        instante(rs.getTimestamp("saida_em")),
                        rs.getLong("segundos")),
                idFilial, de, ate);
        for (PartCargaRow r : parts) {
            CargaAcc c = cargas.get(r.idViagem());
            if (c != null) {
                c.adicionar(r);
            }
        }

        // 3) Enriquece origem/motorista pelo legado (best-effort; carga sem resumo fica sem).
        Map<Long, ResumoViagemLegado> resumos =
                viagemLegadoRepository.buscarResumoPorViagens(new ArrayList<>(cargas.keySet()));

        List<Carga> lista = new ArrayList<>();
        for (CargaAcc c : cargas.values()) {
            lista.add(c.build(resumos.get(c.base.idViagem())));
        }
        return new RelatorioProdutividadeCargas(de, ate, totais(lista), lista);
    }

    private static Totais totais(List<Carga> cargas) {
        int totalCtes = 0;
        long totalVol = 0;
        BigDecimal totalPeso = BigDecimal.ZERO;
        long horasHomem = 0;
        List<Long> janelas = new ArrayList<>();
        List<Long> efetivos = new ArrayList<>();
        for (Carga c : cargas) {
            totalCtes += c.qtdCtes();
            totalVol += c.volumes();
            totalPeso = totalPeso.add(c.peso() == null ? BigDecimal.ZERO : c.peso());
            horasHomem += c.horasHomemSeg();
            janelas.add(c.janelaSeg());
            efetivos.add(c.efetivoSeg());
        }
        return new Totais(cargas.size(), totalCtes, totalVol, totalPeso,
                mediana(janelas), mediana(efetivos), horasHomem);
    }

    private static long mediana(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0L;
        }
        List<Long> ordenado = new ArrayList<>(valores);
        ordenado.sort(null);
        int meio = ordenado.size() / 2;
        return ordenado.size() % 2 == 1
                ? ordenado.get(meio)
                : (ordenado.get(meio - 1) + ordenado.get(meio)) / 2;
    }

    /** Acumulador de uma carga: base + atividades/participações para tempos e drill-down. */
    private static final class CargaAcc {
        final CargaBaseRow base;
        final Map<Long, AtvCargaAcc> atividades = new LinkedHashMap<>();
        final List<Intervalo> presencas = new ArrayList<>();
        final java.util.Set<Long> operadores = new java.util.LinkedHashSet<>();
        long horasHomemSeg;

        CargaAcc(CargaBaseRow base) {
            this.base = base;
        }

        void adicionar(PartCargaRow r) {
            operadores.add(r.idUsuario());
            horasHomemSeg += r.segundos();
            // fim do intervalo: saída registrada; se nula (não deu "Sair"), usa o fim da atividade.
            Instant fim = r.saida() != null ? r.saida() : r.finalizadaEm();
            presencas.add(new Intervalo(r.entrada(), fim));
            atividades.computeIfAbsent(r.idAtividade(),
                    k -> new AtvCargaAcc(r.idAtividade(), r.status(), r.iniciadaEm(), r.finalizadaEm()))
                    .participantes.add(new ParticipanteCarga(r.idUsuario(), r.nome(), r.entrada(),
                            r.saida(), r.segundos()));
        }

        Carga build(ResumoViagemLegado resumo) {
            long janela = base.inicio() == null || base.fim() == null ? 0L
                    : base.fim().getEpochSecond() - base.inicio().getEpochSecond();
            long efetivo = Intervalos.uniaoSegundos(presencas);
            List<AtividadeCarga> atvs = new ArrayList<>();
            for (AtvCargaAcc a : atividades.values()) {
                atvs.add(a.build());
            }
            String origem = resumo == null ? null : resumo.origem();
            String motorista = resumo == null ? null : resumo.motorista();
            return new Carga(base.idViagem(), base.placa(), origem, motorista, base.tipo(),
                    base.inicio(), base.fim(), janela, efetivo, horasHomemSeg,
                    operadores.size(), base.qtdCtes(), base.volumes(), base.peso(), atvs);
        }
    }

    private static final class AtvCargaAcc {
        final long idAtividade;
        final String status;
        final Instant iniciadaEm;
        final Instant finalizadaEm;
        final List<ParticipanteCarga> participantes = new ArrayList<>();

        AtvCargaAcc(long idAtividade, String status, Instant iniciadaEm, Instant finalizadaEm) {
            this.idAtividade = idAtividade;
            this.status = status;
            this.iniciadaEm = iniciadaEm;
            this.finalizadaEm = finalizadaEm;
        }

        AtividadeCarga build() {
            long janela = iniciadaEm == null || finalizadaEm == null ? 0L
                    : finalizadaEm.getEpochSecond() - iniciadaEm.getEpochSecond();
            return new AtividadeCarga(idAtividade, status, iniciadaEm, finalizadaEm, janela, participantes);
        }
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
