package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cadastro/ativação de filial na Torre (ação ADMIN). O {@code idFilial} é o id
 * da filial no legado; ativar a Torre numa filial = {@code ativa=true} +
 * {@code dataCorteViagem} (só considera viagens com baixa a partir dessa data).
 * Upsert por {@code idFilial}.
 */
public record SalvarFilialRequest(
        @NotNull Integer idFilial,
        @NotBlank @Size(max = 120) String nome,
        @NotNull LocalDate dataCorteViagem,
        boolean ativa
) {
}
