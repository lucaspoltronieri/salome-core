package br.com.salome.core.infrastructure.torre;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Datasource read-write próprio da Torre + migrações Flyway.
 *
 * <p>Toda a configuração é condicional a {@code salome.torre.enabled=true}, então o
 * serviço financeiro não carrega nada disto. O legado permanece o datasource
 * {@code @Primary} (read-only); a Torre é sempre acessada por beans qualificados
 * ({@code torreJdbcTemplate} / {@code torreTransactionManager}).
 *
 * <p>O Flyway é executado <b>explicitamente</b> contra o datasource da Torre — o
 * auto-config do Flyway não está no classpath (Boot 4), garantindo que migração
 * nenhuma rode contra o banco legado.
 */
@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreJdbcConfiguration {

    @Bean(name = "torreDataSource", destroyMethod = "close")
    HikariDataSource torreDataSource(TorreDatabaseProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setReadOnly(false);
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(2);
        config.setPoolName("salome-torre");
        return new HikariDataSource(config);
    }

    @Bean(name = "torreJdbcTemplate")
    JdbcTemplate torreJdbcTemplate(@Qualifier("torreDataSource") DataSource torreDataSource) {
        return new JdbcTemplate(torreDataSource);
    }

    @Bean(name = "torreTransactionManager")
    DataSourceTransactionManager torreTransactionManager(@Qualifier("torreDataSource") DataSource torreDataSource) {
        return new DataSourceTransactionManager(torreDataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway torreFlyway(@Qualifier("torreDataSource") DataSource torreDataSource) {
        return Flyway.configure()
                .dataSource(torreDataSource)
                .locations("classpath:db/migration/torre")
                .baselineOnMigrate(true)
                .table("flyway_schema_history_torre")
                .load();
    }
}
