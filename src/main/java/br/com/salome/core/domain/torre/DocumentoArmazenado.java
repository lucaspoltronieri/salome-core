package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Documento operacional fisicamente presente no armazém da Torre, já resolvido
 * com o box/local onde está. Visão de leitura para a tela "Armazém atual" e para
 * o dashboard (ocupação por box). Distingue, pelo {@code status}, o que está
 * aguardando separação (NO_ARMAZEM) do que já está pronto (SEPARADO_BOX).
 *
 * <p>As datas fiscais/logísticas são enriquecidas em {@code ArmazemService}: emissão
 * e previsão vêm do legado; chegada vem da baixa de transferência no legado ou da
 * descarga de coleta registrada na Torre.
 */
public record DocumentoArmazenado(
        long id,
        Integer numeroCte,
        boolean preCte,
        Integer volumes,
        BigDecimal peso,
        String remetente,
        String destinatario,
        String cidadeDestino,
        LocalDate dataEmissao,
        LocalDate dataChegada,
        LocalDate dataPrevistaEntrega,
        StatusDocumento status,
        Long idLocal,
        String codigoLocal,
        String nomeLocal,
        String tipoLocal,
        Long idConhecimentoLegado,
        Instant atualizadoEm
) {

    /** Cópia com datas enriquecidas para a tela "Armazém atual". */
    public DocumentoArmazenado comDatas(LocalDate emissao, LocalDate chegada, LocalDate previstaEntrega) {
        return new DocumentoArmazenado(id, numeroCte, preCte, volumes, peso, remetente, destinatario,
                cidadeDestino, emissao, chegada, previstaEntrega, status, idLocal, codigoLocal, nomeLocal, tipoLocal,
                idConhecimentoLegado, atualizadoEm);
    }
}
