package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.Objects;

/**
 * Canonical, versioned text document embedded once per solved Problem Session.
 */
public record CanonicalEmbeddingDocument(String version, String text, String fingerprint) {

    /**
     * Creates a validated canonical embedding document.
     */
    public CanonicalEmbeddingDocument {
        version = EmbeddingRequest.requireBounded(version, "version", 128);
        text = EmbeddingRequest.requireBounded(text, "text", 12_000);
        fingerprint = EmbeddingRequest.requireSha256(fingerprint, "fingerprint");
        Objects.requireNonNull(text, "text");
    }
}
