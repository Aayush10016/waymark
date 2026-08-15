package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;

/**
 * Password verification and rehash policy for login.
 */
public interface PasswordVerificationPolicy extends PasswordHashingPolicy {

    /**
     * Returns whether a raw password matches a stored password hash.
     *
     * @param rawPassword raw password candidate
     * @param passwordHash stored password hash and metadata
     * @return true when the password matches
     */
    boolean matches(CharSequence rawPassword, PasswordHashRecord passwordHash);

    /**
     * Returns whether the stored password hash should be rehashed after a
     * successful login.
     *
     * @param passwordHash stored password hash and metadata
     * @return true when rehashing is required
     */
    boolean requiresRehash(PasswordHashRecord passwordHash);
}
