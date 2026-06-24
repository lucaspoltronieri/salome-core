package br.com.salome.core.domain.torre;

/**
 * Indicadores operacionais do dia para o painel TV de uma filial.
 * O "dia" é o dia corrente no fuso do servidor (início = 00:00 local).
 * Durações em segundos para o front formatar como preferir.
 */
public record IndicadoresDia(
        int atividadesFinalizadasHoje,
        long horasHomemHojeSeg,
        int pessoasAtivasAgora,
        int documentosNoArmazem,
        int ocorrenciasHoje,
        long tempoMedioDescargaSeg
) {
}
