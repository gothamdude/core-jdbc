package com.gothamdude.core.jdbc.service;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

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
     * Test database connection with timeout
     */
    public boolean testConnection(int timeoutSeconds) {
        try {
            jdbcTemplate.setQueryTimeout(timeoutSeconds);
            return isHealthy();
        } catch (Exception e) {
            log.error("Database connection test failed with timeout {}: {}", timeoutSeconds, e.getMessage(), e);
            return false;
        } finally {
            // Reset to default timeout
            jdbcTemplate.setQueryTimeout(30);
        }
    }
}
