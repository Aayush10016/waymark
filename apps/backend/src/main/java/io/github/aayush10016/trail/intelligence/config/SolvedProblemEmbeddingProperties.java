package io.github.aayush10016.trail.intelligence.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for solved-session embedding jobs.
 */
@ConfigurationProperties(prefix = "trail.embedding.solved-session")
public record SolvedProblemEmbeddingProperties(String generationKey, Duration timeout) {

    private static final String DEFAULT_GENERATION_KEY = "solved-session";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Creates validated solved-session embedding configuration.
     */
    public SolvedProblemEmbeddingProperties {
        generationKey = generationKey == null || generationKey.isBlank()
                ? DEFAULT_GENERATION_KEY
                : generationKey.strip();
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("solved-session embedding timeout must be positive");
        }
    }
}
