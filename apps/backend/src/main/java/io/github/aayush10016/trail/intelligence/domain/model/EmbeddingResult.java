package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Success-or-failure result for an embedding provider call.
 */
public record EmbeddingResult(EmbeddingResponse response, EmbeddingProviderFailure failure) {

    /**
     * Creates a validated result with exactly one populated branch.
     */
    public EmbeddingResult {
        if ((response == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of response or failure is required");
        }
    }

    /**
     * Creates a successful result.
     */
    public static EmbeddingResult success(EmbeddingResponse response) {
        return new EmbeddingResult(Objects.requireNonNull(response, "response"), null);
    }

    /**
     * Creates a failed result.
     */
    public static EmbeddingResult failure(EmbeddingProviderFailure failure) {
        return new EmbeddingResult(null, Objects.requireNonNull(failure, "failure"));
    }

    /**
     * Whether the provider call succeeded.
     */
    public boolean succeeded() {
        return response != null;
    }

    /**
     * Returns the success branch when present.
     */
    public Optional<EmbeddingResponse> responseOptional() {
        return Optional.ofNullable(response);
    }

    /**
     * Returns the failure branch when present.
     */
    public Optional<EmbeddingProviderFailure> failureOptional() {
        return Optional.ofNullable(failure);
    }
}
