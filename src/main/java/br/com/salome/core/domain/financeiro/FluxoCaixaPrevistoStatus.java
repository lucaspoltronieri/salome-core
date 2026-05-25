package br.com.salome.core.domain.financeiro;

public enum FluxoCaixaPrevistoStatus {
    TODAS("Todas"),
    EM_ABERTO("Em aberto"),
    ATRASADA("Atrasada"),
    VENCE_HOJE("Vence hoje"),
    A_VENCER("A vencer"),
    PAGO("Pago");

    private final String label;

    FluxoCaixaPrevistoStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
