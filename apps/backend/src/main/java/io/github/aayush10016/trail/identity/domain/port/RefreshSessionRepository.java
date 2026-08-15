package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbound port for refresh-session persistence and atomic rotation.
 */
public interface RefreshSessionRepository {

    /**
     * Stores an initial refresh session.
     *
     * @param session hash-only refresh session
     */
    void create(StoredRefreshSession session);

    /**
     * Atomically rotates a presented refresh credential.
     *
     * <p>Implementations must ensure the presented token can only rotate once.
     * If a previously used token from a known family is presented again, the
     * family must be marked compromised so future rotations for that family are
     * rejected.
     *
     * @param presentedTokenHash hash of the client-presented refresh token
     * @param replacement replacement session material
     * @param rotatedAt rotation timestamp
     * @return rotation result
     */
    RotationResult rotate(
            RefreshTokenHash presentedTokenHash, RefreshSessionReplacement replacement, Instant rotatedAt);

    /**
     * Idempotently revokes the family containing the presented current refresh
     * credential.
     *
     * <p>Implementations must not require the raw refresh token. Unknown,
     * expired, already revoked, or compromised credentials must be treated as
     * no-ops from the caller's perspective. If the token belongs to a known
     * family, every active credential in that family must be revoked.
     *
     * @param presentedTokenHash hash of the client-presented refresh token
     * @param revokedAt revocation timestamp
     */
    void revokeCurrentFamily(RefreshTokenHash presentedTokenHash, Instant revokedAt);

    /**
     * Idempotently revokes every refresh-session family owned by a user.
     *
     * @param principal authenticated principal whose refresh families are revoked
     * @param revokedAt revocation timestamp
     */
    void revokeAllFamiliesForUser(AuthenticatedPrincipal principal, Instant revokedAt);

    /**
     * Replacement session material created by the application layer.
     *
     * @param sessionId replacement auth-session ID
     * @param tokenHash replacement refresh-token hash
     * @param expiresAt replacement expiry
     */
    record RefreshSessionReplacement(UUID sessionId, RefreshTokenHash tokenHash, Instant expiresAt) {

        /**
         * Creates validated replacement material.
         */
        public RefreshSessionReplacement {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(tokenHash, "tokenHash");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    /**
     * Refresh rotation result.
     *
     * @param status rotation status
     * @param principal authenticated principal for successful rotations
     */
    record RotationResult(RotationStatus status, AuthenticatedPrincipal principal) {

        /**
         * Creates a validated rotation result.
         */
        public RotationResult {
            Objects.requireNonNull(status, "status");
            if (status == RotationStatus.ROTATED && principal == null) {
                throw new IllegalArgumentException("rotated result requires a principal");
            }
            if (status != RotationStatus.ROTATED && principal != null) {
                throw new IllegalArgumentException("non-rotated result must not include a principal");
            }
        }

        /**
         * Creates a successful rotation result.
         *
         * @param principal authenticated principal
         * @return rotated result
         */
        public static RotationResult rotated(AuthenticatedPrincipal principal) {
            return new RotationResult(RotationStatus.ROTATED, principal);
        }

        /**
         * Creates a replay-detected result.
         *
         * @return replay-detected result
         */
        public static RotationResult replayDetected() {
            return new RotationResult(RotationStatus.REPLAY_DETECTED, null);
        }

        /**
         * Creates a generic inactive result for unknown, expired, revoked, or
         * compromised credentials.
         *
         * @return inactive result
         */
        public static RotationResult notActive() {
            return new RotationResult(RotationStatus.NOT_ACTIVE, null);
        }
    }

    /**
     * Internal refresh rotation status.
     */
    enum RotationStatus {
        /**
         * Presented token was accepted and replaced.
         */
        ROTATED,

        /**
         * Previously used credential was replayed and its family was marked
         * compromised.
         */
        REPLAY_DETECTED,

        /**
         * Credential was unknown, expired, already compromised, or otherwise not
         * active.
         */
        NOT_ACTIVE
    }
}
