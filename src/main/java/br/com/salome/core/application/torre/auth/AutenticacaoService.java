package br.com.salome.core.application.torre.auth;

import br.com.salome.core.domain.torre.auth.LoginRequest;
import br.com.salome.core.domain.torre.auth.LoginResponse;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import br.com.salome.core.infrastructure.torre.auth.JwtService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        UsuarioCredencial credencial = usuarioRepository.buscarPorLogin(request.login())
                .filter(UsuarioCredencial::ativo)
                .filter(u -> passwordEncoder.matches(request.senha(), u.senhaHash()))
                .orElseThrow(() -> new BadCredentialsException("Login ou senha inválidos."));

        UsuarioAutenticado usuario = credencial.toAutenticado();
        JwtService.TokenGerado token = jwtService.gerar(usuario);
        return new LoginResponse(token.token(), token.expiraEm(), usuario);
    }
}
