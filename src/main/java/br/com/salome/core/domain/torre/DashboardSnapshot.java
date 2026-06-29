package br.com.salome.core.domain.torre;

import java.time.Instant;
import java.util.List;

/**
 * Visão de entrada da Torre (web): indicadores do dia, ocupação do armazém por
 * estágio/box e situação do pátio (caminhões chegando, aguardando e coletas em
 * trânsito). Compõe serviços já existentes — não roda consulta nova pesada.
 *
 * <p>Os percentuais de ocupação por estágio o front calcula a partir das
 * contagens ({@code aguardandoSeparacao}…{@code emCarregamento}) sobre
 * {@code totalNoArmazem}.
 */
public record DashboardSnapshot(
        int idFilial,
        Instant atualizadoEm,
        IndicadoresDia indicadores,
        // --- ocupação do armazém (estados próprios da Torre) ---
        int totalNoArmazem,
        int aguardandoSeparacao,
        int emSeparacao,
        int prontos,
        int emCarregamento,
        int avarias,
        List<BoxOcupacao> boxes,
        // --- pátio / recebimento ---
        int caminhoesEmTransito,
        int caminhoesAguardando,
        int coletasEmTransito,
        int descargasEmAndamento,
        String proximaChegadaData,
        String proximaChegadaHora
) {
}
