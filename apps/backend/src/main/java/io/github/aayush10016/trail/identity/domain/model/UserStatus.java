package io.github.aayush10016.trail.identity.domain.model;

/**
 * Supported user account lifecycle states.
 */
public enum UserStatus {
    /**
     * Account exists but email ownership has not been verified.
     */
    PENDING_VERIFICATION,

    /**
     * Account is verified and eligible for login.
     */
    ACTIVE,

    /**
     * Account is locked and not eligible for login.
     */
    LOCKED,

    /**
     * Account deletion has been requested and login is disabled.
     */
    DELETION_REQUESTED,

    /**
     * Account has been deleted and login is disabled.
     */
    DELETED
}
