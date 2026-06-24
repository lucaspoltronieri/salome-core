package br.com.salome.core.domain.torre;

import java.time.LocalDate;

public record FilialTorre(
        int idFilial,
        String nome,
        LocalDate dataCorteViagem,
        boolean ativa
) {
}
