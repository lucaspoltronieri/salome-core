package br.com.salome.core.infrastructure.torre.auth;

import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.TorreProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Geração e validação de JWT da Torre (HS256). Stateless: o token carrega
 * idUsuario, login, nome, idFilial e perfil — sem hit no banco por request.
 */
@Component
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class JwtService {

    private final SecretKey key;
    private final long expiracaoMinutos;
    private final Clock clock;

    public JwtService(TorreProperties properties, Clock clock) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "salome.torre.jwt.secret é obrigatório e deve ter pelo menos 32 caracteres (HS256).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = properties.jwt().effectiveExpirationMinutes();
        this.clock = clock;
    }

    public TokenGerado gerar(UsuarioAutenticado usuario) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(Duration.ofMinutes(expiracaoMinutos));
        String token = Jwts.builder()
                .subject(usuario.login())
                .claim("idUsuario", usuario.id())
                .claim("nome", usuario.nome())
                .claim("idFilial", usuario.idFilial())
                .claim("perfil", usuario.perfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
        return new TokenGerado(token, expira);
    }

    public UsuarioAutenticado validar(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Claims c = jws.getPayload();
        return new UsuarioAutenticado(
                ((Number) c.get("idUsuario")).longValue(),
                c.get("nome", String.class),
                c.getSubject(),
                ((Number) c.get("idFilial")).intValue(),
                PerfilCodigo.valueOf(c.get("perfil", String.class))
        );
    }

    public record TokenGerado(String token, Instant expiraEm) {
    }
}
