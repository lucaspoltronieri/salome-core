package br.com.salome.core.domain.torre;

import java.time.LocalDate;

/** Datas do CT-e lidas do legado para enriquecer visões operacionais da Torre. */
public record ConhecimentoDatas(
        LocalDate dataEmissao,
        LocalDate dataChegada,
        LocalDate dataPrevistaEntrega
) {
}
