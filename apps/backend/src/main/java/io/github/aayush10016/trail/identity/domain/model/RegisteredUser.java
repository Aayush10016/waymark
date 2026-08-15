package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User account created by the registration flow.
 *
 * @param id immutable user identifier
 * @param email normalized and display email
 * @param passwordHash password hash material and metadata
 * @param status account lifecycle status
 * @param createdAt creation timestamp
 */
public record RegisteredUser(
        UUID id, EmailAddress email, PasswordHashRecord passwordHash, UserStatus status, Instant createdAt) {

    /**
     * Creates a validated registered user.
     */
    public RegisteredUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Creates a new unverified account.
     *
     * @param id immutable user identifier
     * @param email normalized and display email
     * @param passwordHash encoded password hash
     * @param createdAt creation timestamp
     * @return unverified registered user
     */
    public static RegisteredUser pendingVerification(
            UUID id, EmailAddress email, PasswordHashRecord passwordHash, Instant createdAt) {
        return new RegisteredUser(id, email, passwordHash, UserStatus.PENDING_VERIFICATION, createdAt);
    }
}
