package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório do que cada operador fez num período: por pessoa, o tempo de cada
 * atividade e a relação de CT-es (com peso, volumes, remetente, destinatário e o
 * tempo aproximado por CT-e, derivado do horário de cada marcação).
 */
public record RelatorioOperadores(LocalDate de, LocalDate ate, List<Operador> operadores) {

    public record Operador(
            long idUsuario,
            String nome,
            int totalAtividades,
            long totalSegundos,
            int totalCtes,
            long totalVolumes,
            BigDecimal totalPeso,
            List<AtividadeOperador> atividades) {
    }

    public record AtividadeOperador(
            long idAtividade,
            String tipo,
            String subtipo,
            String placa,
            String status,
            Instant entradaEm,
            Instant saidaEm,
            long segundos,
            List<CteOperador> ctes) {
    }

    public record CteOperador(
            Integer numeroCte,
            Integer volumes,
            BigDecimal peso,
            String remetente,
            String destinatario,
            String cidadeDestino,
            String status,
            Instant marcadoEm,
            Long segundosNoCte) {
    }
}
