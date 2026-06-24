package br.com.salome.core.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança base da aplicação.
 *
 * <p>O {@code spring-boot-starter-security} trancaria todos os endpoints por padrão.
 * Quando a Torre está desligada (serviço financeiro), liberamos tudo para preservar
 * o modelo atual (Basic Auth do nginx). Quando a Torre está ligada, o chain JWT é
 * definido em {@code TorreSecurityConfig}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain permitAllChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
