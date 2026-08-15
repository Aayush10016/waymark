package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hash-only refresh-session record prepared for persistence.
 *
 * @param id auth-session ID
 * @param sessionFamilyId refresh-session family ID
 * @param principal authenticated principal
 * @param tokenHash stored refresh-token hash
 * @param rotationCounter refresh rotation counter
 * @param createdAt session creation timestamp
 * @param expiresAt refresh-session expiry timestamp
 */
public record StoredRefreshSession(
        UUID id,
        UUID sessionFamilyId,
        AuthenticatedPrincipal principal,
        RefreshTokenHash tokenHash,
        int rotationCounter,
        Instant createdAt,
        Instant expiresAt) {

    /**
     * Creates a validated stored refresh session.
     */
    public StoredRefreshSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionFamilyId, "sessionFamilyId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (rotationCounter < 0) {
            throw new IllegalArgumentException("rotationCounter must not be negative");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}
