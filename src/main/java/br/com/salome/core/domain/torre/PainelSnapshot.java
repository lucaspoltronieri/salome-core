package br.com.salome.core.domain.torre;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot do painel TV de uma filial (consumido por polling).
 */
public record PainelSnapshot(
        int idFilial,
        Instant atualizadoEm,
        IndicadoresDia indicadores,
        List<ViagemAguardando> viagensAguardando,
        List<AtividadeResumo> descargasEmAndamento,
        List<AtividadeResumo> separacoesEmAndamento,
        List<AtividadeResumo> carregamentosEmAndamento,
        List<AtividadeResumo> outrasEmAndamento,
        List<DocumentoOperacional> noArmazem,
        List<Ocorrencia> ocorrenciasRecentes
) {
}
