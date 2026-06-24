package br.com.salome.core.domain.torre;

import java.util.Locale;

/**
 * Filtros do mapa do armazém aplicados no servidor (export) — espelham os
 * filtros da tela. Todos opcionais; em branco = sem filtro.
 */
public record MapaFiltro(String texto, String cidade, String situacao) {

    public static MapaFiltro vazio() {
        return new MapaFiltro(null, null, null);
    }

    private static boolean contemTexto(String alvo, String termo) {
        if (termo == null || termo.isBlank()) {
            return true;
        }
        if (alvo == null) {
            return false;
        }
        return alvo.toLowerCase(Locale.ROOT).contains(termo.toLowerCase(Locale.ROOT));
    }

    /** Casa o termo livre contra qualquer um dos campos informados. */
    public boolean casaTexto(String... campos) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        for (String campo : campos) {
            if (contemTexto(campo, texto)) {
                return true;
            }
        }
        return false;
    }

    public boolean casaCidade(String cidadeAlvo) {
        return contemTexto(cidadeAlvo, cidade);
    }

    public boolean casaSituacao(String situacaoAlvo) {
        return contemTexto(situacaoAlvo, situacao);
    }
}
