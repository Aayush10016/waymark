package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Account material required by the login use case.
 *
 * @param id immutable user ID
 * @param email account email
 * @param passwordHash stored password hash and metadata
 * @param status account lifecycle status
 * @param emailVerifiedAt email verification timestamp when verified
 */
public record LoginAccount(
        UUID id, EmailAddress email, PasswordHashRecord passwordHash, UserStatus status, Instant emailVerifiedAt) {

    /**
     * Creates a validated login account.
     */
    public LoginAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Returns whether the account email has been verified.
     *
     * @return true when verified
     */
    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Creates the authenticated principal for this account.
     *
     * @return authenticated principal
     */
    public AuthenticatedPrincipal toPrincipal() {
        return new AuthenticatedPrincipal(id);
    }
}
