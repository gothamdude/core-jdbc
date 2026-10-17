package com.gothamdude.core.jdbc.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot actuator adapter over {@link DbHealthService}.
 * <p>
 * Only wired when actuator is on the classpath (see {@code DbConfig}), so a
 * consumer that does not use actuator never needs it.
 */
public class DbHealthIndicator implements HealthIndicator {

    private final DbHealthService dbHealthService;

    public DbHealthIndicator(DbHealthService dbHealthService) {
        this.dbHealthService = dbHealthService;
    }

    @Override
    public Health health() {
        if (!dbHealthService.isHealthy()) {
            return Health.down().build();
        }
        try {
            return Health.up().withDetail("database", dbHealthService.getDatabaseInfo()).build();
        } catch (RuntimeException e) {
            // Connection is up but metadata lookup failed — still report UP.
            return Health.up().build();
        }
    }
}
