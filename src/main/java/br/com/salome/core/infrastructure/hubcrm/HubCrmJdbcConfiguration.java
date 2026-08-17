package br.com.salome.core.infrastructure.hubcrm;

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

@Configuration
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmJdbcConfiguration {

    @Bean(name = "hubCrmDataSource", destroyMethod = "close")
    HikariDataSource hubCrmDataSource(HubCrmProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.datasource().url());
        config.setUsername(properties.datasource().username());
        config.setPassword(properties.datasource().password());
        config.setReadOnly(false);
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(1);
        config.setPoolName("salome-hub-crm");
        return new HikariDataSource(config);
    }

    @Bean(name = "hubCrmJdbcTemplate")
    JdbcTemplate hubCrmJdbcTemplate(@Qualifier("hubCrmDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "hubCrmTransactionManager")
    DataSourceTransactionManager hubCrmTransactionManager(
            @Qualifier("hubCrmDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway hubCrmFlyway(@Qualifier("hubCrmDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/hubcrm")
                .baselineOnMigrate(true)
                .table("flyway_schema_history_hub_crm")
                .load();
    }
}
