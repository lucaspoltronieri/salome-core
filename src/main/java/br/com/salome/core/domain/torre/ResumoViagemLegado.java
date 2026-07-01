package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo agregado (1 linha por viagem, não por manifesto) usado para enriquecer a lista
 * de caminhões a separar com os mesmos dados que a lista "aguardando descarga" já mostra.
 */
public record ResumoViagemLegado(
        String placa,
        String motorista,
        String origem,
        int qtdCtes,
        BigDecimal volumes,
        BigDecimal peso,
        LocalDate dataBaixa,
        String horaBaixa,
        int qtdManifestos
) {
}
