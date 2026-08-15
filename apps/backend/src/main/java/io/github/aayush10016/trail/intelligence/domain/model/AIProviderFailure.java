package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;

/**
 * Safe provider failure metadata suitable for application decisions and logs.
 */
public record AIProviderFailure(AIProviderFailureCode code, boolean retryable) {

    /**
     * Creates validated failure metadata.
     */
    public AIProviderFailure {
        Objects.requireNonNull(code, "code");
    }
}
