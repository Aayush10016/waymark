package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Immutable identity for a prompt version.
 */
public record PromptIdentity(String key, int version, String hash) {

    private static final int MAX_KEY_LENGTH = 128;
    private static final int HASH_LENGTH = 64;

    /**
     * Creates a validated prompt identity.
     */
    public PromptIdentity {
        key = requireKey(key);
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (hash == null || !hash.matches("[a-f0-9]{" + HASH_LENGTH + "}")) {
            throw new IllegalArgumentException("hash must be a lowercase SHA-256 hex value");
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        var sanitized = key.strip();
        if (sanitized.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("key must be bounded");
        }
        if (!sanitized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("key contains unsupported characters");
        }
        return sanitized;
    }
}
