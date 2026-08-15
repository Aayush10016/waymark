package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral structured AI request.
 */
public record StructuredAIRequest(
        String model,
        String systemInstruction,
        String userInput,
        String responseSchemaJson,
        Duration timeout,
        Map<String, String> metadata) {

    private static final int MAX_MODEL_LENGTH = 128;
    private static final int MAX_INSTRUCTION_LENGTH = 32_000;
    private static final int MAX_INPUT_LENGTH = 64_000;
    private static final int MAX_SCHEMA_LENGTH = 32_000;
    private static final int MAX_METADATA_KEY_LENGTH = 128;
    private static final int MAX_METADATA_VALUE_LENGTH = 512;

    /**
     * Creates a validated structured AI request.
     */
    public StructuredAIRequest {
        model = requireBoundedText(model, "model", MAX_MODEL_LENGTH);
        systemInstruction = requireBoundedText(systemInstruction, "systemInstruction", MAX_INSTRUCTION_LENGTH);
        userInput = requireBoundedText(userInput, "userInput", MAX_INPUT_LENGTH);
        responseSchemaJson = requireBoundedText(responseSchemaJson, "responseSchemaJson", MAX_SCHEMA_LENGTH);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        metadata.forEach((key, value) -> {
            requireBoundedText(key, "metadata key", MAX_METADATA_KEY_LENGTH);
            requireBoundedText(value, "metadata value", MAX_METADATA_VALUE_LENGTH);
        });
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
