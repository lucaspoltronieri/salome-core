package br.com.salome.core.domain.torre;

import java.time.LocalDate;

/**
 * Resumo de baixa/manifesto de uma viagem (caminhão) de transferência — usado para
 * enriquecer telas de Descarga/Separação com "data de chegada" e contagem de manifestos,
 * sem repetir os joins pesados de CT-e/volume/peso (essas telas já calculam isso localmente).
 */
public record ManifestoResumo(
        long idViagem,
        int qtdManifestos,
        LocalDate dataBaixa,
        String horaBaixa
) {
}
