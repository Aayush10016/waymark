package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral request for embedding one canonical text document.
 */
public record EmbeddingRequest(
        String model,
        String input,
        int dimensions,
        String inputFingerprint,
        String policyVersion,
        Duration timeout,
        Map<String, String> metadata) {

    private static final int MAX_MODEL_LENGTH = 128;
    private static final int MAX_INPUT_LENGTH = 64_000;
    private static final int MAX_POLICY_VERSION_LENGTH = 128;
    private static final int MAX_METADATA_KEY_LENGTH = 128;
    private static final int MAX_METADATA_VALUE_LENGTH = 512;

    /**
     * Creates a validated embedding request.
     */
    public EmbeddingRequest {
        model = requireBounded(model, "model", MAX_MODEL_LENGTH);
        input = requireBounded(input, "input", MAX_INPUT_LENGTH);
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        inputFingerprint = requireSha256(inputFingerprint, "inputFingerprint");
        policyVersion = requireBounded(policyVersion, "policyVersion", MAX_POLICY_VERSION_LENGTH);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        metadata.forEach((key, value) -> {
            requireBounded(key, "metadata key", MAX_METADATA_KEY_LENGTH);
            requireBounded(value, "metadata value", MAX_METADATA_VALUE_LENGTH);
        });
    }

    static String requireSha256(String value, String fieldName) {
        var sanitized = requireBounded(value, fieldName, 64);
        if (!sanitized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + " must be a SHA-256 hex digest");
        }
        return sanitized;
    }

    static String requireBounded(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var sanitized = value.strip();
        if (sanitized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        return sanitized;
    }
}
