package br.com.salome.core.domain.torre.erro;

/** Usuário autenticado sem permissão para a ação (ex.: operador acessando auditoria). Mapeado para HTTP 403. */
public class AcessoNegado extends RuntimeException {

    public AcessoNegado(String mensagem) {
        super(mensagem);
    }
}
