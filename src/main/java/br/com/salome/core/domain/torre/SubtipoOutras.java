package br.com.salome.core.domain.torre;

import java.util.Optional;

/**
 * Catálogo fixo de subtipos da atividade {@link TipoAtividade#OUTRAS}.
 * Substitui o texto livre: o app mostra botões e o backend só aceita estes códigos.
 * O {@code codigo} é o que vai gravado na coluna {@code subtipo}.
 */
public enum SubtipoOutras {

    LANCAR_DADOS("Lançar dados no sistema"),
    ATENDIMENTO("Atendimento ao cliente"),
    LIMPEZA_ARMAZEM("Limpeza e arrumação do armazém"),
    LIMPEZA_VEICULOS("Limpeza de veículos"),
    OUTRAS("Outras atividade");

    private final String rotulo;

    SubtipoOutras(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    public static Optional<SubtipoOutras> porCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (SubtipoOutras s : values()) {
            if (s.name().equalsIgnoreCase(codigo.trim())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
