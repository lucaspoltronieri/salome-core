package br.com.salome.core.domain.torre;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Os três boxes-destino padrão da Torre, identificados pelo {@code codigo} do
 * {@link LocalArmazem} (único por filial). O destino é escolhido no momento da
 * descarga e define o status do documento:
 *
 * <ul>
 *   <li><b>Separação</b> → o CT-e ainda precisa ser separado ({@code NO_ARMAZEM},
 *       cai na fila de separar).</li>
 *   <li><b>Distribuição</b> / <b>Transferência</b> → pronto, pula a separação
 *       ({@code SEPARADO_BOX}); a distinção entre eles é pelo box (local), não pelo status.</li>
 * </ul>
 *
 * Opções válidas por origem da descarga:
 * transferência → {Distribuição, Separação}; coleta → {Transferência, Separação}.
 */
public enum BoxPadrao {

    SEPARACAO("SEP", "Box Separação", StatusDocumento.NO_ARMAZEM),
    DISTRIBUICAO("DIST", "Box Distribuição", StatusDocumento.SEPARADO_BOX),
    TRANSFERENCIA("TRANSF", "Box Transferência", StatusDocumento.SEPARADO_BOX);

    /** Tipo de {@link LocalArmazem} usado para os boxes-destino. */
    public static final String TIPO = "BOX";

    /** Destinos válidos numa descarga de transferência. */
    public static final Set<BoxPadrao> DESTINOS_TRANSFERENCIA = EnumSet.of(DISTRIBUICAO, SEPARACAO);

    /** Destinos válidos numa descarga de coleta. */
    public static final Set<BoxPadrao> DESTINOS_COLETA = EnumSet.of(TRANSFERENCIA, SEPARACAO);

    private final String codigo;
    private final String nome;
    private final StatusDocumento statusAposDescarga;

    BoxPadrao(String codigo, String nome, StatusDocumento statusAposDescarga) {
        this.codigo = codigo;
        this.nome = nome;
        this.statusAposDescarga = statusAposDescarga;
    }

    public String codigo() {
        return codigo;
    }

    public String nome() {
        return nome;
    }

    public StatusDocumento statusAposDescarga() {
        return statusAposDescarga;
    }

    public static Optional<BoxPadrao> porCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (BoxPadrao box : values()) {
            if (box.codigo.equalsIgnoreCase(codigo.trim())) {
                return Optional.of(box);
            }
        }
        return Optional.empty();
    }
}
