package br.com.salome.core.application.torre.auth;

import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import java.util.Optional;

/**
 * Porta de leitura de usuários da Torre (banco próprio).
 */
public interface UsuarioRepository {

    Optional<UsuarioCredencial> buscarPorLogin(String login);
}
