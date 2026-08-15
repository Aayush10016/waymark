package io.github.aayush10016.trail.identity.security;

/**
 * Versioned metadata stored alongside a password hash.
 *
 * @param algorithm password hashing algorithm identifier
 * @param version Trail password hashing policy version
 * @param parameters canonical parameter string for the policy version
 */
public record PasswordHashMetadata(String algorithm, int version, String parameters) {

    /**
     * Creates validated password hash metadata.
     */
    public PasswordHashMetadata {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (parameters == null || parameters.isBlank()) {
            throw new IllegalArgumentException("parameters must not be blank");
        }
    }
}
