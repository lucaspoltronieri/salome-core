package br.com.salome.core.infrastructure.torre.auth;

import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acesso ao usuário autenticado e resolução da filial ativa.
 *
 * <p>OPERADOR fica travado na própria filial. ADMIN pode informar uma filial
 * alvo (parâmetro/header); se não informar, usa a própria.
 */
public final class AutenticacaoContexto {

    private AutenticacaoContexto() {
    }

    public static UsuarioAutenticado usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new IllegalStateException("Nenhum usuário autenticado no contexto.");
    }

    /**
     * Resolve a filial que a requisição deve enxergar.
     *
     * @param filialSolicitada filial pedida (admin); pode ser null
     */
    public static int filialAtiva(Integer filialSolicitada) {
        UsuarioAutenticado usuario = usuarioAtual();
        if (usuario.isAdmin() && filialSolicitada != null) {
            return filialSolicitada;
        }
        return usuario.idFilial();
    }
}
