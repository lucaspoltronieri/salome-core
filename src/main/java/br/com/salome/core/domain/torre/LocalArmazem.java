package br.com.salome.core.domain.torre;

/**
 * Local físico do armazém (doca, box, área, pendência, avaria...).
 */
public record LocalArmazem(
        Long id,
        int idFilial,
        String codigo,
        String nome,
        String tipo,
        boolean ativo
) {
}
