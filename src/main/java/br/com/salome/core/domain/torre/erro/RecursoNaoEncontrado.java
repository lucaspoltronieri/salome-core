package br.com.salome.core.domain.torre.erro;

/** Recurso inexistente (ou fora da filial do usuário). Mapeado para HTTP 404. */
public class RecursoNaoEncontrado extends RuntimeException {

    public RecursoNaoEncontrado(String mensagem) {
        super(mensagem);
    }
}
