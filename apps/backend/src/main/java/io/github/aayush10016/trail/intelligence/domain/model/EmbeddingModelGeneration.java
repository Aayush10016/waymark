package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned embedding model generation metadata.
 */
public record EmbeddingModelGeneration(
        UUID id,
        String generationKey,
        int generationVersion,
        String provider,
        String model,
        int dimensions,
        boolean active,
        Instant createdAt) {

    /**
     * Creates validated generation metadata.
     */
    public EmbeddingModelGeneration {
        Objects.requireNonNull(id, "id");
        generationKey = EmbeddingRequest.requireBounded(generationKey, "generationKey", 128);
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        provider = EmbeddingRequest.requireBounded(provider, "provider", 64);
        model = EmbeddingRequest.requireBounded(model, "model", 128);
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
