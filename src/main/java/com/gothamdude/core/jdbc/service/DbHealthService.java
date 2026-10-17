package com.gothamdude.core.jdbc.service;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@RequiredArgsConstructor
public class DbHealthService {

    private final JdbcTemplate  jdbcTemplate;
    /**
     * Check if database connection is healthy
     */
    public boolean isHealthy() {
        try {
            // Execute a simple query to test connectivity
            Integer result = jdbcTemplate.queryForObject("SELECT 1",Integer.class);
            boolean healthy = result != null && result == 1;

            if (healthy) {
                log.info("Database connection is up!!!");
            } else {
                log.warn("Database connection test return unexpected result: {}", result);
            }

            return healthy;

        } catch (Exception e) {
            log.error("Error checking database health: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get database product and version
     *
     */
    public String getDatabaseInfo() {
        try {
            return jdbcTemplate.execute((Connection connection) -> {
                DatabaseMetaData metaData = connection.getMetaData();
                return String.format("%s %s",
                        metaData.getDatabaseProductName(),
                        metaData.getDatabaseProductVersion());
            });
        } catch (Exception e) {
            log.error("Failed to get database info: {}", e.getMessage(), e);
            throw new CoreJdbcException(CoreJdbcException.ErrorCode.CONNECTION_FAILURE,"Failed to retrieve database information", e);
        }
    }

    /**
     * Test database connection, applying the timeout to this probe only.
     * <p>
     * The timeout is set on the per-call {@link Statement} rather than on the
     * shared {@link JdbcTemplate}, so this never mutates state other callers
     * rely on and there is nothing to "reset" afterwards.
     */
    public boolean testConnection(int timeoutSeconds) {
        try {
            Integer result = jdbcTemplate.execute((StatementCallback<Integer>) statement -> {
                statement.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = statement.executeQuery("SELECT 1")) {
                    return rs.next() ? rs.getInt(1) : null;
                }
            });
            boolean healthy = result != null && result == 1;
            if (healthy) {
                log.info("Database connection is up!!!");
            } else {
                log.warn("Database connection test returned unexpected result: {}", result);
            }
            return healthy;
        } catch (Exception e) {
            log.error("Database connection test failed with timeout {}: {}", timeoutSeconds, e.getMessage(), e);
            return false;
        }
    }
}
