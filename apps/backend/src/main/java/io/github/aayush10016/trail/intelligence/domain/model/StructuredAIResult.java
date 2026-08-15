package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Success-or-failure result for a structured AI call.
 */
public record StructuredAIResult(StructuredAIResponse response, AIProviderFailure failure) {

    /**
     * Creates a validated result with exactly one populated branch.
     */
    public StructuredAIResult {
        if ((response == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of response or failure is required");
        }
    }

    /**
     * Creates a successful result.
     */
    public static StructuredAIResult success(StructuredAIResponse response) {
        return new StructuredAIResult(Objects.requireNonNull(response, "response"), null);
    }

    /**
     * Creates a failed result.
     */
    public static StructuredAIResult failure(AIProviderFailure failure) {
        return new StructuredAIResult(null, Objects.requireNonNull(failure, "failure"));
    }

    /**
     * Whether the result is successful.
     */
    public boolean succeeded() {
        return response != null;
    }

    /**
     * Returns the response branch when successful.
     */
    public Optional<StructuredAIResponse> responseOptional() {
        return Optional.ofNullable(response);
    }

    /**
     * Returns the failure branch when failed.
     */
    public Optional<AIProviderFailure> failureOptional() {
        return Optional.ofNullable(failure);
    }
}
