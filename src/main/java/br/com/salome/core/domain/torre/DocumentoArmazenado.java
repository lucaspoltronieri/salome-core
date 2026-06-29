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
 * <p>{@code dataEmissao} não é coluna própria da Torre: vem do legado
 * ({@code conhecimento.cteEmissao}) e é enriquecida em {@code ArmazemService} a
 * partir de {@code idConhecimentoLegado}; fica nula para pré-CTes sem CT-e casado.
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
        StatusDocumento status,
        Long idLocal,
        String codigoLocal,
        String nomeLocal,
        String tipoLocal,
        Long idConhecimentoLegado,
        Instant atualizadoEm
) {

    /** Cópia com a data de emissão enriquecida do legado. */
    public DocumentoArmazenado comDataEmissao(LocalDate emissao) {
        return new DocumentoArmazenado(id, numeroCte, preCte, volumes, peso, remetente, destinatario,
                cidadeDestino, emissao, status, idLocal, codigoLocal, nomeLocal, tipoLocal,
                idConhecimentoLegado, atualizadoEm);
    }
}
