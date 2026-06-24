package br.com.salome.core.application.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso a usuários da Torre (banco próprio).
 */
public interface UsuarioRepository {

    Optional<UsuarioCredencial> buscarPorLogin(String login);

    long criar(String login, String nome, String senhaHash, int idFilial, PerfilCodigo perfil);

    List<UsuarioResumo> listar(int idFilial);

    /** Ativa/desativa o usuário. Retorna true se algum registro foi alterado. */
    boolean definirAtivo(long id, boolean ativo);
}
