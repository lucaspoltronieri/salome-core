package br.com.salome.core.domain.torre;

/**
 * Status operacional do documento (CT-e) ao longo do fluxo físico do armazém.
 * Catálogo da proposta §11.
 */
public enum StatusDocumento {
    AGUARDANDO_DESCARGA,
    EM_DESCARGA,
    NO_ARMAZEM,
    EM_SEPARACAO,
    SEPARADO_BOX,
    EM_CARREGAMENTO,
    CARREGADO,
    CROSS_DOCK_DIRETO,
    AVARIA,
    DIVERGENCIA
}
