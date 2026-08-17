package br.com.salome.core.domain.hubcrm;

public enum LossReason {
    HIGH_PRICE("Preço alto"),
    BAD_DEADLINE_WINDOW("Prazo e janela ruins"),
    SPECIAL_DEDICATED_CARGO("Carga especial/dedicada"),
    OUTSIDE_IDEAL_PROFILE("Fora do perfil ideal"),
    NO_CREDIT("Sem crédito"),
    NO_INSURANCE("Sem Seguro"),
    FREIGHT_REGRET("Arrependimento do frete"),
    PICKUP_PROBLEM("Problema na coleta"),
    DEFAULT("Inadimplência"),
    COMPETITOR("Perda para concorrente");

    private final String arpaName;

    LossReason(String arpaName) {
        this.arpaName = arpaName;
    }

    public String arpaName() {
        return arpaName;
    }
}
