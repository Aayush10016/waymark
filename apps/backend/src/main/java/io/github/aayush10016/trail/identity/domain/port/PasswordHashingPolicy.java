package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;

/**
 * Outbound port for password hashing policy behavior used by identity use cases.
 */
public interface PasswordHashingPolicy {

    /**
     * Returns whether a raw password is within the accepted input size.
     *
     * @param rawPassword raw password candidate
     * @return true when the password can be hashed
     */
    boolean isWithinMaximumInputSize(CharSequence rawPassword);

    /**
     * Hashes a raw password using the current policy.
     *
     * @param rawPassword raw password
     * @return encoded hash and metadata
     */
    PasswordHashRecord hash(CharSequence rawPassword);
}
