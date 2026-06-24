package br.com.salome.core.domain.torre.erro;

/** Violação de regra operacional (ex.: finalizar atividade já finalizada). Mapeado para HTTP 409. */
public class RegraViolada extends RuntimeException {

    public RegraViolada(String mensagem) {
        super(mensagem);
    }
}
