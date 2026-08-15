package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import java.time.Instant;

/**
 * Outbound port for the email verification token lifecycle.
 */
public interface EmailVerificationTokenRepository {

    /**
     * Stores a verification token for a pending account with the requested
     * normalized email.
     *
     * @param email requested email
     * @param token token metadata containing only the hashed token
     * @return internal store result
     */
    StoreResult storeForPendingEmail(EmailAddress email, StoredEmailVerificationToken token);

    /**
     * Consumes a valid active verification token exactly once and marks the
     * owning user verified.
     *
     * @param tokenHash token hash
     * @param consumedAt consumption timestamp
     * @return internal consumption result
     */
    ConsumeResult consume(EmailActionTokenHash tokenHash, Instant consumedAt);

    /**
     * Internal resend result. Application responses remain generic.
     */
    enum StoreResult {
        /**
         * Token was stored for a pending user.
         */
        STORED,

        /**
         * No pending user can receive a verification token.
         */
        NO_PENDING_USER
    }

    /**
     * Internal consume result. Application responses remain generic.
     */
    enum ConsumeResult {
        /**
         * Token was active and consumed.
         */
        CONSUMED,

        /**
         * Token was missing, expired, or already consumed.
         */
        NOT_ACTIVE
    }
}
