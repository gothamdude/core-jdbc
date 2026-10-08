package com.gothamdude.core.jdbc.config;


import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbConfigTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private JdbcTemplate mockJdbcTemplate;

    private DbConfig dbConfig;
    private DbProperties dbProperties;

    @BeforeEach
    void setUp() {
        dbProperties = new DbProperties();
        dbProperties.setUrl("jdbc:h2:mem:unit_test_db;DB_CLOSE_DELAY=-1");
        dbProperties.setUsername("sa");
        dbProperties.setPassword("");
        dbProperties.setDriverClassName("org.h2.Driver");
        dbProperties.setMaxPoolSize(5);
        dbProperties.setMinPoolSize(1);
        dbProperties.setQueryTimeout(15);
        dbProperties.setFetchSize(200);
        dbConfig = new DbConfig(dbProperties);
    }

    // --- jdbcTemplate ---

    @Test
    void shouldCreateJdbcTemplateWithCorrectQueryTimeout() {
        JdbcTemplate jdbcTemplate = dbConfig.jdbcTemplate(mockDataSource);
        assertThat(jdbcTemplate.getQueryTimeout()).isEqualTo(15);
    }

    @Test
    void shouldCreateJdbcTemplateWithCorrectFetchSize() {
        JdbcTemplate jdbcTemplate = dbConfig.jdbcTemplate(mockDataSource);
        assertThat(jdbcTemplate.getFetchSize()).isEqualTo(200);
    }

    @Test
    void shouldCreateJdbcTemplateWithProvidedDataSource() {
        JdbcTemplate jdbcTemplate = dbConfig.jdbcTemplate(mockDataSource);
        assertThat(jdbcTemplate.getDataSource()).isSameAs(mockDataSource);
    }

    // --- namedParameterJdbcTemplate ---

    @Test
    void shouldCreateNamedParameterJdbcTemplateWrappingJdbcTemplate() {
        when(mockJdbcTemplate.getDataSource()).thenReturn(mockDataSource);
        NamedParameterJdbcTemplate namedTemplate = dbConfig.namedParameterJdbcTemplate(mockJdbcTemplate);

        assertThat(namedTemplate).isNotNull();
        assertThat(namedTemplate.getJdbcTemplate()).isSameAs(mockJdbcTemplate);
    }

    // --- transactionManager ---

    @Test
    void shouldCreateDataSourceTransactionManager() {
        PlatformTransactionManager txManager = dbConfig.transactionManager(mockDataSource);
        assertThat(txManager).isInstanceOf(DataSourceTransactionManager.class);
    }

    @Test
    void transactionManagerShouldUseProvidedDataSource() {
        DataSourceTransactionManager txManager =
                (DataSourceTransactionManager) dbConfig.transactionManager(mockDataSource);
        assertThat(txManager.getDataSource()).isSameAs(mockDataSource);
    }

    // --- dataSource (HikariCP creation — uses H2 to avoid needing PostgreSQL) ---

    @Test
    void shouldCreateHikariDataSourceWithCorrectMaxPoolSize() {
        HikariDataSource ds = dbConfig.dataSource();
        try {
            assertThat(ds.getMaximumPoolSize()).isEqualTo(5);
        } finally {
            ds.close();
        }
    }

    @Test
    void shouldCreateHikariDataSourceWithCorrectMinIdle() {
        HikariDataSource ds = dbConfig.dataSource();
        try {
            assertThat(ds.getMinimumIdle()).isEqualTo(1);
        } finally {
            ds.close();
        }
    }

    @Test
    void shouldCreateHikariDataSourceWithCorrectJdbcUrl() {
        HikariDataSource ds = dbConfig.dataSource();
        try {
            assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:h2:mem:unit_test_db;DB_CLOSE_DELAY=-1");
        } finally {
            ds.close();
        }
    }

    @Test
    void shouldCreateHikariDataSourceWithCorrectDriverClassName() {
        HikariDataSource ds = dbConfig.dataSource();
        try {
            assertThat(ds.getDriverClassName()).isEqualTo("org.h2.Driver");
        } finally {
            ds.close();
        }
    }

    @Test
    void shouldCreateHikariDataSourceWithConnectionTimeout() {
        HikariDataSource ds = dbConfig.dataSource();
        try {
            // queryTimeout (15s) * 1000 = 15000ms
            assertThat(ds.getConnectionTimeout()).isEqualTo(15_000L);
        } finally {
            ds.close();
        }
    }
}