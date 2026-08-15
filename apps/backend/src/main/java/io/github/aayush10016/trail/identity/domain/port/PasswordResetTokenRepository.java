package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import java.time.Instant;
import java.util.Objects;

/**
 * Outbound port for password-reset token request storage.
 */
public interface PasswordResetTokenRepository {

    /**
     * Stores a reset token for an active account matching the requested email.
     *
     * @param email requested email
     * @param token token metadata containing only the hashed token
     * @return internal store result
     */
    StoreResult storeForActiveEmail(EmailAddress email, StoredPasswordResetToken token);

    /**
     * Consumes a valid active reset token exactly once and updates the owning
     * user's password hash.
     *
     * @param tokenHash reset-token hash
     * @param newPasswordHash replacement password hash
     * @param consumedAt consumption timestamp
     * @return internal consumption result
     */
    ConsumeResult consume(EmailActionTokenHash tokenHash, PasswordHashRecord newPasswordHash, Instant consumedAt);

    /**
     * Internal reset-token store status. Application responses remain generic.
     */
    enum StoreStatus {
        /**
         * Token was stored for an active user.
         */
        STORED,

        /**
         * No active user can receive a password-reset token.
         */
        NO_ACTIVE_USER
    }

    /**
     * Internal reset-token store result.
     *
     * @param status storage status
     * @param recipient canonical recipient when stored
     */
    record StoreResult(StoreStatus status, EmailAddress recipient) {

        /**
         * Creates a validated store result.
         */
        public StoreResult {
            Objects.requireNonNull(status, "status");
            if (status == StoreStatus.STORED && recipient == null) {
                throw new IllegalArgumentException("stored result requires a recipient");
            }
            if (status != StoreStatus.STORED && recipient != null) {
                throw new IllegalArgumentException("non-stored result must not include a recipient");
            }
        }

        /**
         * Creates a stored result.
         *
         * @param recipient canonical recipient
         * @return stored result
         */
        public static StoreResult stored(EmailAddress recipient) {
            return new StoreResult(StoreStatus.STORED, recipient);
        }

        /**
         * Creates a generic no-active-user result.
         *
         * @return no-active-user result
         */
        public static StoreResult noActiveUser() {
            return new StoreResult(StoreStatus.NO_ACTIVE_USER, null);
        }
    }

    /**
     * Internal reset-token consumption status. Application responses remain
     * generic.
     */
    enum ConsumeStatus {
        /**
         * Token was active, consumed once, and password hash was updated.
         */
        CONSUMED,

        /**
         * Token was missing, expired, or already consumed.
         */
        NOT_ACTIVE
    }

    /**
     * Internal reset-token consumption result.
     *
     * @param status consumption status
     * @param principal authenticated principal for successful consumption
     */
    record ConsumeResult(ConsumeStatus status, AuthenticatedPrincipal principal) {

        /**
         * Creates a validated consumption result.
         */
        public ConsumeResult {
            Objects.requireNonNull(status, "status");
            if (status == ConsumeStatus.CONSUMED && principal == null) {
                throw new IllegalArgumentException("consumed result requires a principal");
            }
            if (status != ConsumeStatus.CONSUMED && principal != null) {
                throw new IllegalArgumentException("non-consumed result must not include a principal");
            }
        }

        /**
         * Creates a consumed result.
         *
         * @param principal authenticated principal for the reset account
         * @return consumed result
         */
        public static ConsumeResult consumed(AuthenticatedPrincipal principal) {
            return new ConsumeResult(ConsumeStatus.CONSUMED, principal);
        }

        /**
         * Creates a generic inactive result.
         *
         * @return not-active result
         */
        public static ConsumeResult notActive() {
            return new ConsumeResult(ConsumeStatus.NOT_ACTIVE, null);
        }
    }
}
