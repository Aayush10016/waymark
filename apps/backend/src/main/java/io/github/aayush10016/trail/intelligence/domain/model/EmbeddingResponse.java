package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral successful embedding response with vector metadata.
 */
public record EmbeddingResponse(
        String provider,
        String model,
        int dimensions,
        String inputFingerprint,
        List<Double> vector,
        AIUsageMetadata usage,
        AICostMetadata cost) {

    /**
     * Creates a validated embedding response.
     */
    public EmbeddingResponse {
        provider = EmbeddingRequest.requireBounded(provider, "provider", 64);
        model = EmbeddingRequest.requireBounded(model, "model", 128);
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        inputFingerprint = EmbeddingRequest.requireSha256(inputFingerprint, "inputFingerprint");
        vector = List.copyOf(Objects.requireNonNull(vector, "vector"));
        if (vector.size() != dimensions) {
            throw new IllegalArgumentException("vector size must match dimensions");
        }
        vector.forEach(value -> {
            Objects.requireNonNull(value, "vector value");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("vector values must be finite");
            }
        });
        usage = Objects.requireNonNull(usage, "usage");
        cost = Objects.requireNonNull(cost, "cost");
    }
}
