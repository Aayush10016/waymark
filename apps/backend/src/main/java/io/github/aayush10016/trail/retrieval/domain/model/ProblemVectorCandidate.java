package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Exact vector candidate for a solved Problem Session.
 */
public record ProblemVectorCandidate(
        UUID problemSessionId,
        String generationKey,
        int generationVersion,
        String provider,
        String model,
        String inputFingerprint,
        double distance) {

    /**
     * Creates a validated vector candidate.
     */
    public ProblemVectorCandidate {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        generationKey = requireText(generationKey, "generationKey");
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        inputFingerprint = requireSha256(inputFingerprint);
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static String requireSha256(String value) {
        var normalized = requireText(value, "inputFingerprint");
        if (!normalized.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("inputFingerprint must be lowercase SHA-256 hex");
        }
        return normalized;
    }
}
