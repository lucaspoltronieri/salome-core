package br.com.salome.core.infrastructure.torre;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de topo do módulo Torre de Controle.
 *
 * <p>O módulo inteiro (datasource RW, sincronizador, API e painel) só é ativado
 * quando {@code salome.torre.enabled=true}, permitindo que o serviço financeiro
 * ({@code salome-web}) e o serviço da Torre ({@code salome-torre}) rodem a partir
 * do mesmo JAR sem se acoplarem.
 */
@ConfigurationProperties(prefix = "salome.torre")
public record TorreProperties(
        boolean enabled,
        Jwt jwt,
        Sync sync,
        Fotos fotos
) {

    public TorreProperties {
        jwt = jwt == null ? new Jwt(null, 0) : jwt;
        sync = sync == null ? new Sync(null) : sync;
        fotos = fotos == null ? new Fotos(null) : fotos;
    }

    public record Fotos(String dir) {
        public String effectiveDir() {
            return dir == null || dir.isBlank() ? "./dados/torre/fotos" : dir;
        }
    }

    public record Jwt(String secret, long expirationMinutes) {
        public long effectiveExpirationMinutes() {
            return expirationMinutes <= 0 ? 720 : expirationMinutes;
        }
    }

    public record Sync(String cron) {
        public String effectiveCron() {
            return cron == null || cron.isBlank() ? "0 */5 * * * *" : cron;
        }
    }
}
