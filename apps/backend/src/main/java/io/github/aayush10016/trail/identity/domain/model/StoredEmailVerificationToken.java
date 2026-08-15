package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistable email verification token metadata. Raw token values are never
 * stored here.
 *
 * @param id token identifier
 * @param tokenHash SHA-256 token hash
 * @param createdAt creation timestamp
 * @param expiresAt expiry timestamp
 */
public record StoredEmailVerificationToken(
        UUID id, EmailActionTokenHash tokenHash, Instant createdAt, Instant expiresAt) {

    /**
     * Creates a validated stored verification token.
     */
    public StoredEmailVerificationToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}
