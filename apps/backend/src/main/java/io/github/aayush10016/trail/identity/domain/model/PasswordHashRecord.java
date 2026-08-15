package io.github.aayush10016.trail.identity.domain.model;

/**
 * Encoded password hash plus policy metadata persisted for rehash decisions.
 *
 * @param encodedHash encoded password hash
 * @param algorithm password hashing algorithm
 * @param parameters canonical policy parameter string
 * @param version policy version
 */
public record PasswordHashRecord(String encodedHash, String algorithm, String parameters, int version) {

    /**
     * Creates a validated password hash record.
     */
    public PasswordHashRecord {
        if (encodedHash == null || encodedHash.isBlank()) {
            throw new IllegalArgumentException("encoded hash must not be blank");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (parameters == null || parameters.isBlank()) {
            throw new IllegalArgumentException("parameters must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

}
