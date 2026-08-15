package io.github.aayush10016.trail.intelligence.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable versioned prompt definition with verified content hash.
 */
public record PromptDefinition(PromptIdentity identity, String body, String outputSchemaJson) {

    private static final int MAX_BODY_LENGTH = 32_000;
    private static final int MAX_SCHEMA_LENGTH = 32_000;

    /**
     * Creates a verified prompt definition.
     */
    public PromptDefinition {
        Objects.requireNonNull(identity, "identity");
        body = requireBoundedText(body, "body", MAX_BODY_LENGTH);
        outputSchemaJson = requireBoundedText(outputSchemaJson, "outputSchemaJson", MAX_SCHEMA_LENGTH);
        var computedHash = contentHash(body, outputSchemaJson);
        if (!identity.hash().equals(computedHash)) {
            throw new IllegalArgumentException("prompt hash does not match key/version content");
        }
    }

    /**
     * Computes the canonical prompt content hash.
     */
    public static String contentHash(String body, String outputSchemaJson) {
        var canonical = requireBoundedText(body, "body", MAX_BODY_LENGTH)
                + "\n---schema---\n"
                + requireBoundedText(outputSchemaJson, "outputSchemaJson", MAX_SCHEMA_LENGTH);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
