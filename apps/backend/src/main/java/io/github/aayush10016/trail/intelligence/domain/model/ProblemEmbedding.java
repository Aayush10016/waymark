package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Active or superseded vector embedding for one Problem Session.
 */
public record ProblemEmbedding(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        String generationKey,
        int generationVersion,
        String provider,
        String model,
        int dimensions,
        String inputFingerprint,
        String policyVersion,
        List<Double> vector,
        boolean active,
        Instant supersededAt,
        Instant createdAt) {

    /**
     * Creates a validated Problem Session embedding.
     */
    public ProblemEmbedding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        generationKey = EmbeddingRequest.requireBounded(generationKey, "generationKey", 128);
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        provider = EmbeddingRequest.requireBounded(provider, "provider", 64);
        model = EmbeddingRequest.requireBounded(model, "model", 128);
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        inputFingerprint = EmbeddingRequest.requireSha256(inputFingerprint, "inputFingerprint");
        policyVersion = EmbeddingRequest.requireBounded(policyVersion, "policyVersion", 128);
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
        if (active && supersededAt != null) {
            throw new IllegalArgumentException("active embeddings must not be superseded");
        }
        if (!active) {
            Objects.requireNonNull(supersededAt, "supersededAt");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
