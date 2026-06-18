package br.com.salome.core.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "salome.legacy.datasource")
public record LegacyDatabaseProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        boolean readOnly
) {

    public boolean isConfigured() {
        return enabled && url != null && !url.isBlank();
    }
}
