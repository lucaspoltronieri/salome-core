package br.com.salome.core.domain.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;

/**
 * Dados de um usuário para listagem/admin — sem o hash de senha.
 */
public record UsuarioResumo(
        long id,
        String nome,
        String login,
        int idFilial,
        PerfilCodigo perfil,
        boolean ativo
) {
}
