package br.com.salome.core.infrastructure.hubcrm;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Conexão de escrita no legado com o usuário {@code crm_api}, usada somente para aprovar
 * cotações. O pool somente leitura do legado ({@code legacyJdbcTemplate}) continua sendo o
 * único usado nas consultas.
 */
@Configuration
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class HubCrmLegacyWriteConfiguration {

    @Bean(name = "hubCrmLegacyWriteDataSource", destroyMethod = "close")
    HikariDataSource hubCrmLegacyWriteDataSource(HubCrmAutoApprovalProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.legacyUrl());
        config.setUsername(properties.legacyUsername());
        config.setPassword(properties.legacyPassword());
        config.setReadOnly(false);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setPoolName("salome-legacy-crm-api");
        return new HikariDataSource(config);
    }

    @Bean(name = "legacyWriteJdbcTemplate")
    JdbcTemplate legacyWriteJdbcTemplate(@Qualifier("hubCrmLegacyWriteDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
