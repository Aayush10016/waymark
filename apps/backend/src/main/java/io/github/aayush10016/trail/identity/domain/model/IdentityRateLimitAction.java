package io.github.aayush10016.trail.identity.domain.model;

/**
 * Identity actions that require abuse throttling.
 */
public enum IdentityRateLimitAction {

    /**
     * Email/password registration attempt.
     */
    REGISTRATION,

    /**
     * Email/password login attempt.
     */
    LOGIN,

    /**
     * Email verification email resend/request.
     */
    EMAIL_VERIFICATION_REQUEST,

    /**
     * Email verification token consumption.
     */
    EMAIL_VERIFICATION_CONSUME,

    /**
     * Password reset email request.
     */
    PASSWORD_RESET_REQUEST,

    /**
     * Password reset token consumption.
     */
    PASSWORD_RESET_CONSUME
}
