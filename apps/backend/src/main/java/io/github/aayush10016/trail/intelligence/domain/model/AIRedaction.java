package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Count of redactions applied for one redaction type.
 */
public record AIRedaction(AIRedactionType type, int count) {

    /**
     * Creates a validated redaction count.
     */
    public AIRedaction {
        java.util.Objects.requireNonNull(type, "type");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
