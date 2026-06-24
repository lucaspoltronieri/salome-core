package br.com.salome.core.infrastructure.torre.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Chain de segurança do serviço da Torre (JWT, stateless).
 *
 * <ul>
 *   <li>{@code /api/torre/auth/login} e o painel estático: liberados;</li>
 *   <li>{@code /api/torre/**}: exigem JWT válido;</li>
 *   <li>demais rotas (financeiro): liberadas (Basic Auth do nginx).</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreSecurityConfig {

    @Bean
    SecurityFilterChain torreChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/torre/auth/login").permitAll()
                        .requestMatchers("/api/torre/painel/**").permitAll()
                        .requestMatchers("/torre/**", "/api/versao").permitAll()
                        .requestMatchers("/api/torre/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
