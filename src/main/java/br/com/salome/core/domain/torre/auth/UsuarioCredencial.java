package br.com.salome.core.domain.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;

/**
 * Usuário com hash de senha, usado apenas internamente na autenticação.
 */
public record UsuarioCredencial(
        long id,
        String nome,
        String login,
        String senhaHash,
        int idFilial,
        PerfilCodigo perfil,
        boolean ativo
) {

    public UsuarioAutenticado toAutenticado() {
        return new UsuarioAutenticado(id, nome, login, idFilial, perfil);
    }
}
