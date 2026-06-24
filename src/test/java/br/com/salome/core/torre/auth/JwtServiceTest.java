package br.com.salome.core.torre.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.TorreProperties;
import br.com.salome.core.infrastructure.torre.auth.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres!!";

    private TorreProperties props(long expMin) {
        return new TorreProperties(true, new TorreProperties.Jwt(SECRET, expMin), new TorreProperties.Sync(null),
                new TorreProperties.Fotos(null));
    }

    private final UsuarioAutenticado usuario =
            new UsuarioAutenticado(7L, "João Operador", "joao", 3, PerfilCodigo.OPERADOR);

    @Test
    void gerarEValidar_roundTrip() {
        JwtService service = new JwtService(props(60), Clock.systemUTC());

        JwtService.TokenGerado gerado = service.gerar(usuario);
        UsuarioAutenticado validado = service.validar(gerado.token());

        assertThat(validado).isEqualTo(usuario);
        assertThat(gerado.expiraEm()).isAfter(Instant.now());
    }

    @Test
    void validar_tokenExpirado_lancaExcecao() {
        Instant passado = Instant.parse("2020-01-01T00:00:00Z");
        Clock relogioFixo = Clock.fixed(passado, ZoneOffset.UTC);
        JwtService service = new JwtService(props(1), relogioFixo);

        String token = service.gerar(usuario).token();

        // valida com relógio atual -> token de 2020 está expirado
        JwtService atual = new JwtService(props(1), Clock.systemUTC());
        assertThatThrownBy(() -> atual.validar(token)).isInstanceOf(Exception.class);
    }

    @Test
    void construtor_secretCurto_falha() {
        TorreProperties ruim = new TorreProperties(true, new TorreProperties.Jwt("curto", 60), new TorreProperties.Sync(null),
                new TorreProperties.Fotos(null));
        assertThatThrownBy(() -> new JwtService(ruim, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiracao_respeitaConfiguracao() {
        Clock fixo = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(props(120), fixo);

        JwtService.TokenGerado gerado = service.gerar(usuario);

        assertThat(gerado.expiraEm()).isEqualTo(Instant.parse("2026-06-23T12:00:00Z").plus(Duration.ofMinutes(120)));
    }
}
