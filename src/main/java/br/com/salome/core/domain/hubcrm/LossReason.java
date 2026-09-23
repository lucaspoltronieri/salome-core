package br.com.salome.core.domain.hubcrm;

/** Motivos de perda ativos: nome no ArpaSuite e coluna correspondente na cotação do legado. */
public enum LossReason {
    HIGH_PRICE("Preço alto", "naoAprovacaoPreco"),
    BAD_DEADLINE_WINDOW("Prazo e janela ruins", "naoAprovacaoPrazo"),
    SPECIAL_DEDICATED_CARGO("Carga especial/dedicada", "naoAprovacaoCargaEspecial"),
    OUTSIDE_IDEAL_PROFILE("Fora do perfil ideal", "naoAprovacaoForaPerfil"),
    NO_CREDIT("Sem crédito", "naoAprovacaoSemCredito"),
    NO_INSURANCE("Sem Seguro", "naoAprovacaoSemSeguro"),
    FREIGHT_REGRET("Arrependimento do frete", "naoAprovacaoArrependimentoFrete"),
    PICKUP_PROBLEM("Problema na coleta", "naoAprovacaoProblemaColeta"),
    DEFAULT("Inadimplência", "naoAprovacaoInadimplencia"),
    COMPETITOR("Perda para concorrente", "naoAprovacaoConcorrente");

    private final String arpaName;
    private final String legacyColumn;

    LossReason(String arpaName, String legacyColumn) {
        this.arpaName = arpaName;
        this.legacyColumn = legacyColumn;
    }

    public String arpaName() {
        return arpaName;
    }

    /** Coluna "Sim/Não" da tela de não aprovação do legado. */
    public String legacyColumn() {
        return legacyColumn;
    }
}
