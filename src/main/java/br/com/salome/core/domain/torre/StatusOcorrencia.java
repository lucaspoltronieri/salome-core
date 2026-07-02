package br.com.salome.core.domain.torre;

import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.util.Set;

/**
 * Ciclo de tratamento de uma ocorrência de avaria.
 *
 * <pre>
 * REGISTRADA → EM_ANALISE → AGUARDANDO_PAGAMENTO → RESOLVIDA → FINALIZADA
 * </pre>
 * CANCELADA é acessível a partir de qualquer estado não-final.
 */
public enum StatusOcorrencia {
    REGISTRADA,
    EM_ANALISE,
    AGUARDANDO_PAGAMENTO,
    RESOLVIDA,
    FINALIZADA,
    CANCELADA;

    private static final Set<StatusOcorrencia> FINAIS = Set.of(FINALIZADA, CANCELADA);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public static StatusOcorrencia de(String valor) {
        try {
            return StatusOcorrencia.valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RegraViolada("Status de ocorrência inválido: " + valor);
        }
    }

    /** Valida a transição a partir deste estado; lança {@link RegraViolada} se proibida. */
    public void validarTransicao(StatusOcorrencia novo) {
        if (this == novo) {
            return;
        }
        if (isFinal()) {
            throw new RegraViolada("Ocorrência " + this + " está encerrada e não pode mudar de status.");
        }
        boolean permitido = switch (novo) {
            case CANCELADA -> true; // de qualquer não-final
            case EM_ANALISE -> this == REGISTRADA;
            case AGUARDANDO_PAGAMENTO -> this == EM_ANALISE;
            case RESOLVIDA -> this == EM_ANALISE || this == AGUARDANDO_PAGAMENTO;
            case FINALIZADA -> this == RESOLVIDA || this == AGUARDANDO_PAGAMENTO;
            case REGISTRADA -> false;
        };
        if (!permitido) {
            throw new RegraViolada("Transição de status inválida: " + this + " → " + novo + ".");
        }
    }
}
