package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;

/**
 * Provider-neutral successful structured AI response.
 */
public record StructuredAIResponse(
        String provider,
        String model,
        String outputJson,
        AIUsageMetadata usage,
        AICostMetadata cost) {

    private static final int MAX_PROVIDER_LENGTH = 64;
    private static final int MAX_MODEL_LENGTH = 128;
    private static final int MAX_OUTPUT_LENGTH = 128_000;

    /**
     * Creates a validated structured AI response.
     */
    public StructuredAIResponse {
        provider = requireBoundedText(provider, "provider", MAX_PROVIDER_LENGTH);
        model = requireBoundedText(model, "model", MAX_MODEL_LENGTH);
        outputJson = requireBoundedText(outputJson, "outputJson", MAX_OUTPUT_LENGTH);
        usage = Objects.requireNonNull(usage, "usage");
        cost = Objects.requireNonNull(cost, "cost");
    }

    private static String requireBoundedText(String value, String fieldName, int maxLength) {
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
