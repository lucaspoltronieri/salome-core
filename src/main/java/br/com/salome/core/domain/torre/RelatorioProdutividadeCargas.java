package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de produtividade por carga (caminhão que chegou e descarregou) num período.
 * Cada carga agrega as atividades de descarga da mesma viagem ({@code id_viagem_legado}).
 *
 * <p>Três leituras de tempo, propositalmente lado a lado:
 * <ul>
 *   <li><b>janela</b>: relógio de parede do início da 1ª descarga ao fim da última
 *       (inclui tempo parado);</li>
 *   <li><b>efetivo</b>: união dos intervalos de presença dos participantes (desconta o
 *       buraco em que ninguém estava presente — <i>desde que deem "Sair"</i>);</li>
 *   <li><b>horasHomem</b>: soma das presenças por pessoa (mede paralelismo/nº de gente).</li>
 * </ul>
 */
public record RelatorioProdutividadeCargas(LocalDate de, LocalDate ate, Totais totais, List<Carga> cargas) {

    /** KPIs do período (cabeçalho). Medianas ficam robustas a outliers. */
    public record Totais(
            int qtdCargas,
            int totalCtes,
            long totalVolumes,
            BigDecimal totalPeso,
            long janelaMedianaSeg,
            long efetivoMedianaSeg,
            long horasHomemTotalSeg) {
    }

    public record Carga(
            Long idViagem,
            String placa,
            String origem,
            String motorista,
            String tipo,
            Instant inicio,
            Instant fim,
            long janelaSeg,
            long efetivoSeg,
            long horasHomemSeg,
            int qtdOperadores,
            int qtdCtes,
            long volumes,
            BigDecimal peso,
            List<AtividadeCarga> atividades) {
    }

    public record AtividadeCarga(
            long idAtividade,
            String status,
            Instant iniciadaEm,
            Instant finalizadaEm,
            long janelaSeg,
            List<ParticipanteCarga> participantes) {
    }

    public record ParticipanteCarga(
            long idUsuario,
            String nome,
            Instant entradaEm,
            Instant saidaEm,
            long segundos) {
    }
}
