package io.github.aayush10016.trail.identity.application.dto;

/**
 * Refresh-session rotation status.
 */
public enum RefreshSessionStatus {
    /**
     * Refresh credential was accepted and rotated.
     */
    ROTATED,

    /**
     * Refresh credential was rejected generically.
     */
    REJECTED
}
