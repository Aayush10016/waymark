package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;

/**
 * Bounded resource evidence used as context for Problem Session enrichment.
 */
public record ProblemSessionEnrichmentResource(String type, String title, String hostname, String queryText) {

    /**
     * Creates validated bounded resource context.
     */
    public ProblemSessionEnrichmentResource {
        type = requireBounded(type, "type", 64);
        title = optionalBounded(title, "title", 120);
        hostname = optionalBounded(hostname, "hostname", 253);
        queryText = optionalBounded(queryText, "queryText", 240);
    }

    /**
     * Renders a content-bounded text line for redaction input.
     */
    public String asText() {
        var parts = new java.util.ArrayList<String>();
        parts.add(type);
        if (title != null) {
            parts.add(title);
        }
        if (hostname != null) {
            parts.add(hostname);
        }
        if (queryText != null) {
            parts.add(queryText);
        }
        return String.join(" | ", parts);
    }

    private static String optionalBounded(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireBounded(value, fieldName, maxLength);
    }

    private static String requireBounded(String value, String fieldName, int maxLength) {
        Objects.requireNonNull(value, fieldName);
        var sanitized = value.strip();
        if (sanitized.isBlank() || sanitized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        return sanitized;
    }
}
