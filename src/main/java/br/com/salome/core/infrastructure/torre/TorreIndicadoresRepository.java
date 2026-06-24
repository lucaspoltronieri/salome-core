package br.com.salome.core.infrastructure.torre;

import br.com.salome.core.application.torre.IndicadoresRepository;
import br.com.salome.core.domain.torre.IndicadoresDia;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Calcula os indicadores do dia em poucas consultas agregadas no banco da Torre.
 * Durações são medidas no servidor (TIMESTAMPDIFF) para não depender do relógio
 * do navegador do painel.
 */
@Repository
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreIndicadoresRepository implements IndicadoresRepository {

    private final JdbcTemplate jdbc;

    public TorreIndicadoresRepository(@Qualifier("torreJdbcTemplate") JdbcTemplate torreJdbcTemplate) {
        this.jdbc = torreJdbcTemplate;
    }

    @Override
    public IndicadoresDia calcular(int idFilial, Instant inicioDia) {
        Timestamp inicio = Timestamp.from(inicioDia);

        int atividadesFinalizadas = intOf(jdbc.queryForObject("""
                SELECT COUNT(*) FROM atividade_armazem
                 WHERE id_filial = ? AND status = 'FINALIZADA' AND finalizada_em >= ?
                """, Integer.class, idFilial, inicio));

        long horasHomemSeg = longOf(jdbc.queryForObject("""
                SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, p.entrada_em, COALESCE(p.saida_em, NOW()))), 0)
                  FROM atividade_participante p
                  JOIN atividade_armazem a ON a.id = p.id_atividade
                 WHERE a.id_filial = ? AND a.status = 'FINALIZADA' AND a.finalizada_em >= ?
                """, Long.class, idFilial, inicio));

        int pessoasAtivas = intOf(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT p.id_usuario)
                  FROM atividade_participante p
                  JOIN atividade_armazem a ON a.id = p.id_atividade
                 WHERE a.id_filial = ? AND a.status = 'ABERTA' AND p.saida_em IS NULL
                """, Integer.class, idFilial));

        int documentosNoArmazem = intOf(jdbc.queryForObject("""
                SELECT COUNT(*) FROM documento_operacional
                 WHERE id_filial = ? AND status = 'NO_ARMAZEM'
                """, Integer.class, idFilial));

        int ocorrenciasHoje = intOf(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ocorrencia_operacional
                 WHERE id_filial = ? AND criado_em >= ?
                """, Integer.class, idFilial, inicio));

        long tempoMedioDescargaSeg = longOf(jdbc.queryForObject("""
                SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, iniciada_em, finalizada_em)), 0)
                  FROM atividade_armazem
                 WHERE id_filial = ? AND status = 'FINALIZADA' AND finalizada_em >= ?
                   AND tipo IN ('DESCARGA_TRANSFERENCIA', 'DESCARGA_COLETA')
                """, Long.class, idFilial, inicio));

        return new IndicadoresDia(atividadesFinalizadas, horasHomemSeg, pessoasAtivas,
                documentosNoArmazem, ocorrenciasHoje, tempoMedioDescargaSeg);
    }

    private static int intOf(Integer v) {
        return v == null ? 0 : v;
    }

    private static long longOf(Long v) {
        return v == null ? 0L : v;
    }
}
