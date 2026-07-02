package br.com.salome.core.domain.torre;

/** CT-e amarrado a uma ocorrência (herdado/selecionado da atividade/viagem). */
public record OcorrenciaCte(
        Long id,
        Long numeroCte,
        Long idDocumento,
        String remetente,
        String destinatario
) {
}
