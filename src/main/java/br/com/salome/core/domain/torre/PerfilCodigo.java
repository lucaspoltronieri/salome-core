package br.com.salome.core.domain.torre;

/**
 * Perfis de acesso com escopo de filial.
 * OPERADOR vê só a própria filial; ADMIN vê todas, filtrando uma por vez.
 */
public enum PerfilCodigo {
    OPERADOR,
    ADMIN,
    /** Chapa: mão de obra esporádica, entra na atividade pelo líder, não acessa o app. */
    CHAPA
}
