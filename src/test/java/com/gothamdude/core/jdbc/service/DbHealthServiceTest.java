package com.gothamdude.core.jdbc.service;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbHealthServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DbHealthService dbHealthService;

    @BeforeEach
    void setUp() {
        dbHealthService = new DbHealthService(jdbcTemplate);
    }

    // --- isHealthy ---

    @Test
    void isHealthyShouldReturnTrueWhenSelectOneReturnsOne() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertThat(dbHealthService.isHealthy()).isTrue();
    }

    @Test
    void isHealthyShouldReturnFalseWhenSelectOneReturnsNull() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);
        assertThat(dbHealthService.isHealthy()).isFalse();
    }

    @Test
    void isHealthyShouldReturnFalseWhenSelectOneReturnsUnexpectedValue() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(0);
        assertThat(dbHealthService.isHealthy()).isFalse();
    }

    @Test
    void isHealthyShouldReturnFalseOnException() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new RuntimeException("DB unavailable"));
        assertThat(dbHealthService.isHealthy()).isFalse();
    }

    // --- getDatabaseInfo ---

    @Test
    void getDatabaseInfoShouldReturnFormattedProductNameAndVersion() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(metaData.getDatabaseProductVersion()).thenReturn("2.2.224");
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });

        assertThat(dbHealthService.getDatabaseInfo()).isEqualTo("H2 2.2.224");
    }

    @Test
    void getDatabaseInfoShouldThrowCoreJdbcExceptionOnFailure() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
                .thenThrow(new RuntimeException("connection error"));

        assertThatThrownBy(() -> dbHealthService.getDatabaseInfo())
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to retrieve database information");
    }

    @Test
    void getDatabaseInfoExceptionShouldHaveConnectionFailureCode() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
                .thenThrow(new RuntimeException("connection error"));

        assertThatThrownBy(() -> dbHealthService.getDatabaseInfo())
                .isInstanceOf(CoreJdbcException.class)
                .satisfies(ex -> assertThat(((CoreJdbcException) ex).getErrorCode())
                        .isEqualTo(CoreJdbcException.ErrorCode.CONNECTION_FAILURE));
    }

    @Test
    void getDatabaseInfoShouldPreserveCause() {
        RuntimeException cause = new RuntimeException("jdbc error");
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenThrow(cause);

        assertThatThrownBy(() -> dbHealthService.getDatabaseInfo())
                .isInstanceOf(CoreJdbcException.class)
                .hasCause(cause);
    }

    // --- testConnection ---

    @Test
    void testConnectionShouldReturnTrueWhenDatabaseIsHealthy() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertThat(dbHealthService.testConnection(5)).isTrue();
    }

    @Test
    void testConnectionShouldSetTimeoutBeforeQuery() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        dbHealthService.testConnection(5);
        verify(jdbcTemplate).setQueryTimeout(5);
    }

    @Test
    void testConnectionShouldResetTimeoutToThirtyInFinally() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        dbHealthService.testConnection(5);
        verify(jdbcTemplate).setQueryTimeout(30);
    }

    @Test
    void testConnectionShouldReturnFalseWhenSetTimeoutThrows() {
        // Only stub the specific timeout value (1); the finally-block reset to 30 must NOT throw
        doThrow(new RuntimeException("cannot set timeout")).when(jdbcTemplate).setQueryTimeout(1);
        assertThat(dbHealthService.testConnection(1)).isFalse();
    }

    @Test
    void testConnectionShouldStillResetTimeoutWhenQueryFails() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new RuntimeException("query failed"));
        dbHealthService.testConnection(5);
        // finally block should still reset to 30
        verify(jdbcTemplate).setQueryTimeout(30);
    }
}