package io.github.aayush10016.trail.bootstrap.config;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database adapter wiring for the Trail backend.
 */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(TrailDatabaseProperties.class)
public class TrailDataSourceConfiguration {

    /**
     * Creates the PostgreSQL data source.
     */
    @Bean
    public DataSource dataSource(TrailDatabaseProperties properties) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(properties.url());
        dataSource.setUser(properties.username());
        dataSource.setPassword(properties.password());
        return dataSource;
    }

    /**
     * Creates the JDBC template.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Creates the transaction manager used by application use cases.
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
