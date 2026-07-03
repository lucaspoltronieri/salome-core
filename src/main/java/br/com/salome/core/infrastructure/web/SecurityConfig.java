package br.com.salome.core.infrastructure.web;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança base da aplicação (serviço financeiro; a Torre tem o seu proprio chain JWT em
 * {@code TorreSecurityConfig}, ativo so quando {@code salome.torre.enabled=true}).
 *
 * <p>Com {@code salome.financeiro.auth.enabled=false} (padrao) libera tudo, preservando o modelo
 * atual (Basic Auth do nginx). Com {@code true}, exige login por formulario com usuarios em memoria
 * (ver {@link FinanceiroAuthProperties}), redirecionando para {@code /login.html} e, apos o login,
 * para o Fluxo de Caixa.
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
    SecurityFilterChain financeiroChain(HttpSecurity http, FinanceiroAuthProperties auth, PasswordEncoder encoder)
            throws Exception {
        if (!auth.enabled()) {
            http
                    .csrf(csrf -> csrf.disable())
                    .httpBasic(basic -> basic.disable())
                    .formLogin(form -> form.disable())
                    .authorizeHttpRequests(req -> req.anyRequest().permitAll());
            return http.build();
        }

        http
                // Formulario de login e uma pagina estatica (sem template para embutir o token) e o
                // acesso hoje ja e sem CSRF (Basic Auth do nginx). Mantemos desligado por paridade.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/login.html", "/login", "/logout", "/api/versao", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/financeiro/fluxo-caixa/", true)
                        .failureUrl("/login.html?erro=1")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?saiu=1")
                        .permitAll())
                .userDetailsService(usuarios(auth, encoder));
        return http.build();
    }

    /** Usuarios em memoria a partir da configuracao. Senha em BCrypt (hash direto ou texto codificado). */
    private UserDetailsService usuarios(FinanceiroAuthProperties auth, PasswordEncoder encoder) {
        List<UserDetails> lista = auth.users().stream()
                .map(usuario -> User.withUsername(usuario.username())
                        .password(codificar(usuario.password(), encoder))
                        .roles(usuario.rolesOrDefault().toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(lista);
    }

    private String codificar(String senha, PasswordEncoder encoder) {
        String valor = senha == null ? "" : senha;
        // Ja e um hash BCrypt ($2a/$2b/$2y): usa como esta. Senao, codifica o texto puro.
        return valor.startsWith("$2") ? valor : encoder.encode(valor);
    }
}
