package br.com.salome.core.infrastructure.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usuarios do login (form login) do serviço financeiro.
 *
 * <p>O acesso hoje e controlado pelo Basic Auth do nginx. Ao ativar {@code enabled=true}, a propria
 * aplicacao passa a exigir login por formulario, com os usuarios definidos aqui (em memoria — nao ha
 * banco gravavel no serviço financeiro). Enquanto {@code enabled=false} (padrao), o comportamento
 * historico e preservado (tudo liberado; controle fica no nginx).
 *
 * <p>A senha de cada usuario pode ser informada como <b>hash BCrypt</b> (comeca com {@code $2}) ou
 * como <b>texto puro</b> (codificado em BCrypt no startup). Exemplo (application.yml):
 *
 * <pre>
 * salome:
 *   financeiro:
 *     auth:
 *       enabled: true
 *       users:
 *         - username: salome
 *           password: "$2a$10$..."   # hash BCrypt (recomendado)
 *           roles: [USER]
 * </pre>
 */
@ConfigurationProperties(prefix = "salome.financeiro.auth")
public record FinanceiroAuthProperties(boolean enabled, List<User> users) {

    public FinanceiroAuthProperties {
        users = users == null ? List.of() : users;
    }

    public record User(String username, String password, List<String> roles) {
        public List<String> rolesOrDefault() {
            return roles == null || roles.isEmpty() ? List.of("USER") : roles;
        }
    }
}
