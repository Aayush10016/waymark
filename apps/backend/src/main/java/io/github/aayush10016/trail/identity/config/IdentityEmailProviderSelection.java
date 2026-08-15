package io.github.aayush10016.trail.identity.config;

/**
 * Supported identity email delivery providers.
 */
public enum IdentityEmailProviderSelection {
    /**
     * Local in-memory adapter for development and tests.
     */
    LOCAL,

    /**
     * Brevo transactional email API for staging, private beta, and production.
     */
    BREVO
}
