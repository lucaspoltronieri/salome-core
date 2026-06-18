package br.com.salome.core.domain.legacy;

public record LegacyOrigin(
        String classe,
        String metodoOuBotao,
        String daoOuQuery,
        String tabela
) {

    public static LegacyOrigin of(String classe, String metodoOuBotao, String daoOuQuery, String tabela) {
        return new LegacyOrigin(classe, metodoOuBotao, daoOuQuery, tabela);
    }

    public String descricao() {
        return classe + "::" + metodoOuBotao + " | " + daoOuQuery + " | " + tabela;
    }
}
