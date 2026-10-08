package com.gothamdude.core.jdbc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DbConfig bean wiring using H2 as the backing DataSource.
 * Each bean factory method in DbConfig is exercised with a real Spring context.
 */
@SpringJUnitConfig(DbConfigIntegrationTest.TestConfig.class)
class DbConfigIntegrationTest {

    @Configuration
    static class TestConfig {

        private DbProperties testDbProperties() {
            DbProperties props = new DbProperties();
            props.setUrl("jdbc:h2:mem:config_integration_db;DB_CLOSE_DELAY=-1");
            props.setUsername("sa");
            props.setPassword("");
            props.setDriverClassName("org.h2.Driver");
            props.setMaxPoolSize(3);
            props.setMinPoolSize(1);
            props.setQueryTimeout(10);
            props.setFetchSize(50);
            return props;
        }

        /**
         * Use SimpleDriverDataSource for context-level wiring (no connection pool overhead).
         * HikariCP creation is tested separately via direct method call.
         */
        @Bean
        public DataSource dataSource() {
            SimpleDriverDataSource ds = new SimpleDriverDataSource();
            ds.setDriverClass(org.h2.Driver.class);
            ds.setUrl("jdbc:h2:mem:config_integration_db;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new DbConfig(testDbProperties()).jdbcTemplate(dataSource);
        }

        @Bean
        public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
            return new DbConfig(testDbProperties()).namedParameterJdbcTemplate(jdbcTemplate);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DbConfig(testDbProperties()).transactionManager(dataSource);
        }
    }

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    // --- DataSource ---

    @Test
    void dataSourceBeanShouldBePresent() {
        assertThat(dataSource).isNotNull();
    }

    // --- JdbcTemplate ---

    @Test
    void jdbcTemplateShouldBeConfiguredWithCorrectQueryTimeout() {
        assertThat(jdbcTemplate.getQueryTimeout()).isEqualTo(10);
    }

    @Test
    void jdbcTemplateShouldBeConfiguredWithCorrectFetchSize() {
        assertThat(jdbcTemplate.getFetchSize()).isEqualTo(50);
    }

    @Test
    void jdbcTemplateShouldBeAbleToExecuteQuery() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void jdbcTemplateShouldUseCorrectDataSource() {
        assertThat(jdbcTemplate.getDataSource()).isSameAs(dataSource);
    }

    // --- NamedParameterJdbcTemplate ---

    @Test
    void namedParameterJdbcTemplateShouldBePresent() {
        assertThat(namedParameterJdbcTemplate).isNotNull();
    }

    @Test
    void namedParameterJdbcTemplateShouldWrapJdbcTemplate() {
        assertThat(namedParameterJdbcTemplate.getJdbcTemplate()).isSameAs(jdbcTemplate);
    }

    @Test
    void namedParameterJdbcTemplateShouldExecuteQuery() {
        Integer result = namedParameterJdbcTemplate.getJdbcTemplate()
                .queryForObject("SELECT 42", Integer.class);
        assertThat(result).isEqualTo(42);
    }

    // --- TransactionManager ---

    @Test
    void transactionManagerShouldBeDataSourceTransactionManager() {
        assertThat(transactionManager).isInstanceOf(DataSourceTransactionManager.class);
    }

    @Test
    void transactionManagerShouldUseCorrectDataSource() {
        DataSourceTransactionManager txMgr = (DataSourceTransactionManager) transactionManager;
        assertThat(txMgr.getDataSource()).isSameAs(dataSource);
    }

    // --- HikariCP creation (direct call — verifies pool configuration) ---

    @Test
    void dataSourceMethodShouldCreateHikariDataSourceWithCorrectSettings() {
        DbProperties props = new DbProperties();
        props.setUrl("jdbc:h2:mem:hikari_cfg_test;DB_CLOSE_DELAY=-1");
        props.setUsername("sa");
        props.setPassword("");
        props.setDriverClassName("org.h2.Driver");
        props.setMaxPoolSize(4);
        props.setMinPoolSize(2);
        props.setQueryTimeout(20);
        props.setFetchSize(100);

        HikariDataSource ds = new DbConfig(props).dataSource();
        try {
            assertThat(ds.getMaximumPoolSize()).isEqualTo(4);
            assertThat(ds.getMinimumIdle()).isEqualTo(2);
            assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:h2:mem:hikari_cfg_test;DB_CLOSE_DELAY=-1");
            assertThat(ds.getDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(ds.getConnectionTimeout()).isEqualTo(20_000L);
        } finally {
            ds.close();
        }
    }
}
