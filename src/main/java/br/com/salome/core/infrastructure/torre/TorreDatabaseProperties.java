package br.com.salome.core.infrastructure.torre;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexão com o banco próprio da Torre (MySQL read-write, servidor separado do legado).
 */
@ConfigurationProperties(prefix = "salome.torre.datasource")
public record TorreDatabaseProperties(
        String url,
        String username,
        String password
) {

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }
}
