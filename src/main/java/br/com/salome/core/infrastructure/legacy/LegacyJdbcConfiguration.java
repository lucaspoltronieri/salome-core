package br.com.salome.core.infrastructure.legacy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class LegacyJdbcConfiguration {

    @Bean(name = "legacyDataSource", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "true")
    HikariDataSource legacyDataSource(LegacyDatabaseProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setReadOnly(properties.readOnly());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setPoolName("salome-legacy-readonly");
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "salome.legacy.datasource", name = "enabled", havingValue = "true")
    JdbcTemplate legacyJdbcTemplate(DataSource legacyDataSource) {
        return new JdbcTemplate(legacyDataSource);
    }
}
