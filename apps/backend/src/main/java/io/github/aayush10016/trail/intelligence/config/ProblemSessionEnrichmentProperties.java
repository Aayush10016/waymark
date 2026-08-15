package io.github.aayush10016.trail.intelligence.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for structured Problem Session enrichment.
 */
@ConfigurationProperties(prefix = "trail.ai.enrichment")
public record ProblemSessionEnrichmentProperties(String model, Duration timeout) {

    private static final String DEFAULT_MODEL = "trail-mock-enrichment";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Creates validated enrichment configuration.
     */
    public ProblemSessionEnrichmentProperties {
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("enrichment timeout must be positive");
        }
    }
}
