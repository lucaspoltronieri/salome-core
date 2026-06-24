package br.com.salome.core.domain.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;

/**
 * Identidade do usuário logado, carregada no JWT e no contexto de segurança.
 */
public record UsuarioAutenticado(
        long id,
        String nome,
        String login,
        int idFilial,
        PerfilCodigo perfil
) {

    public boolean isAdmin() {
        return perfil == PerfilCodigo.ADMIN;
    }
}
