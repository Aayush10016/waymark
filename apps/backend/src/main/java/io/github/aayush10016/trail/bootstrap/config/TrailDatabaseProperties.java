package io.github.aayush10016.trail.bootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Database connection properties for Trail backend adapters.
 */
@ConfigurationProperties(prefix = "trail.database")
public record TrailDatabaseProperties(String url, String username, String password) {

    /**
     * Creates validated database properties.
     */
    public TrailDatabaseProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("trail.database.url must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("trail.database.username must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("trail.database.password must not be null");
        }
    }
}
