package br.com.salome.core.torre.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.application.torre.auth.AutenticacaoService;
import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.LoginRequest;
import br.com.salome.core.domain.torre.auth.LoginResponse;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.infrastructure.torre.TorreProperties;
import br.com.salome.core.infrastructure.torre.auth.JwtService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AutenticacaoServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(
            new TorreProperties(true,
                    new TorreProperties.Jwt("segredo-de-teste-com-mais-de-32-caracteres!!", 60),
                    new TorreProperties.Sync(null),
                    new TorreProperties.Fotos(null)),
            Clock.systemUTC());

    private UsuarioCredencial credencial(boolean ativo) {
        return new UsuarioCredencial(1L, "João", "joao", encoder.encode("senha123"), 3, PerfilCodigo.OPERADOR, ativo);
    }

    private AutenticacaoService service(UsuarioRepository repo) {
        return new AutenticacaoService(repo, encoder, jwtService);
    }

    /** Stub que só responde {@code buscarPorLogin}; cadastro não é exercido aqui. */
    private UsuarioRepository repo(Optional<UsuarioCredencial> resultado) {
        return new UsuarioRepository() {
            @Override
            public Optional<UsuarioCredencial> buscarPorLogin(String login) {
                return resultado;
            }

            @Override
            public long criar(String login, String nome, String senhaHash, int idFilial, PerfilCodigo perfil) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<UsuarioResumo> listar(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean definirAtivo(long id, boolean ativo) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void login_credenciaisValidas_retornaTokenEUsuario() {
        AutenticacaoService service = service(repo(Optional.of(credencial(true))));

        LoginResponse resposta = service.login(new LoginRequest("joao", "senha123"));

        assertThat(resposta.token()).isNotBlank();
        assertThat(resposta.usuario().login()).isEqualTo("joao");
        assertThat(resposta.usuario().idFilial()).isEqualTo(3);
        // token deve validar de volta para o mesmo usuário
        assertThat(jwtService.validar(resposta.token())).isEqualTo(resposta.usuario());
    }

    @Test
    void login_senhaErrada_lancaBadCredentials() {
        AutenticacaoService service = service(repo(Optional.of(credencial(true))));
        assertThatThrownBy(() -> service.login(new LoginRequest("joao", "errada")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_usuarioInativo_lancaBadCredentials() {
        AutenticacaoService service = service(repo(Optional.of(credencial(false))));
        assertThatThrownBy(() -> service.login(new LoginRequest("joao", "senha123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_usuarioInexistente_lancaBadCredentials() {
        AutenticacaoService service = service(repo(Optional.empty()));
        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem", "senha123")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
