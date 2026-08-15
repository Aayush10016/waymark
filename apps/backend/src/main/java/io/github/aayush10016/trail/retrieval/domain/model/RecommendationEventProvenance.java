package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.Objects;

/**
 * Embedding provenance copied onto recommendation lifecycle events.
 */
public record RecommendationEventProvenance(
        String generationKey,
        int generationVersion,
        String provider,
        String model,
        String inputFingerprint) {

    /**
     * Creates validated event provenance.
     */
    public RecommendationEventProvenance {
        generationKey = requireText(generationKey, "generationKey");
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        if (inputFingerprint != null) {
            inputFingerprint = requireText(inputFingerprint, "inputFingerprint");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
