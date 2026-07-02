package br.com.salome.core.domain.torre;

import java.math.BigDecimal;

/** Item avariado (campos livres: código/nome/quantidade; sem valor por item). */
public record OcorrenciaItem(
        Long id,
        String codigo,
        String nome,
        BigDecimal quantidade
) {
}
