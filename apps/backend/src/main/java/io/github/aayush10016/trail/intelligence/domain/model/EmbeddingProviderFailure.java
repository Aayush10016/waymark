package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;

/**
 * Safe embedding provider failure metadata suitable for application decisions and logs.
 */
public record EmbeddingProviderFailure(EmbeddingProviderFailureCode code, boolean retryable) {

    /**
     * Creates validated failure metadata.
     */
    public EmbeddingProviderFailure {
        Objects.requireNonNull(code, "code");
    }
}
