package com.gothamdude.core.jdbc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Slf4j
@RequiredArgsConstructor
@AutoConfiguration
@EnableConfigurationProperties(DbProperties.class)
public class DbConfig {

    private final DbProperties dbProperties;

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.database", name = "url")
    public HikariDataSource dataSource() {
        log.info("Configuring Data Source");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbProperties.getUrl());
        hikariConfig.setUsername(dbProperties.getUsername());
        hikariConfig.setPassword(dbProperties.getPassword());
        hikariConfig.setDriverClassName(dbProperties.getDriverClassName());
        hikariConfig.setMaximumPoolSize(dbProperties.getMaxPoolSize());
        hikariConfig.setMinimumIdle(dbProperties.getMinPoolSize());
        hikariConfig.setConnectionTimeout(dbProperties.getQueryTimeout() * 1000L);
        log.debug("Datasource configured with URL: {}", dbProperties.getUrl());
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcTemplate.class)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        log.info("Configuring JdbcTemplate with dataSource: {} ", dataSource.getClass().getSimpleName());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(dbProperties.getQueryTimeout());
        jdbcTemplate.setFetchSize(dbProperties.getFetchSize());
        return jdbcTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(NamedParameterJdbcTemplate.class)
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
        log.info("Configuring NamedParameterJdbcTemplate with dataSource: {}", jdbcTemplate.getDataSource().getClass().getSimpleName());
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        log.info("Configuring transactionManager with dataSource: {}", dataSource.getClass().getSimpleName());
        return new DataSourceTransactionManager(dataSource);
    }

}
