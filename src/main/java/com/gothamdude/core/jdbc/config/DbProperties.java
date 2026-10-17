package com.gothamdude.core.jdbc.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("app.database")
public class DbProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String username;

    //@NotBlank -- will not work for H2
    private String password;

    private String driverClassName;

    @Min(1)
    private int maxPoolSize = 10;

    @Min(1)
    private int minPoolSize = 2;

    // Max seconds a JDBC statement may run before timing out.
    @Min(1)
    private int queryTimeout = 30;

    // Max seconds to wait for a connection from the pool (Hikari; applied in ms).
    @Min(1)
    private int connectionTimeout = 30;

    @Min(1)
    private int fetchSize = 1000;

}
