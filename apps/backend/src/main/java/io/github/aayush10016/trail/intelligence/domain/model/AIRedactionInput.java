package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Bounded raw input considered for AI redaction.
 */
public record AIRedactionInput(Map<String, String> fields) {

    private static final int MAX_FIELD_NAME_LENGTH = 64;
    private static final int MAX_FIELD_VALUE_LENGTH = 16_000;
    private static final int MAX_FIELDS = 32;

    /**
     * Creates a validated input.
     */
    public AIRedactionInput {
        fields = Map.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("fields must be bounded");
        }
        fields.forEach((key, value) -> {
            requireBoundedText(key, "field key", MAX_FIELD_NAME_LENGTH);
            requireBoundedText(value, "field value", MAX_FIELD_VALUE_LENGTH);
        });
    }

    private static String requireBoundedText(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        return value;
    }
}
