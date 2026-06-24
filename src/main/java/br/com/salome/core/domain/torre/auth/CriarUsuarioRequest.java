package br.com.salome.core.domain.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de usuário da Torre (ação ADMIN). A senha vem em claro e é
 * convertida em hash bcrypt no serviço.
 */
public record CriarUsuarioRequest(
        @NotBlank @Size(max = 60) String login,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(min = 6, max = 100, message = "Senha deve ter ao menos 6 caracteres.") String senha,
        @NotNull Integer idFilial,
        @NotNull PerfilCodigo perfil
) {
}
