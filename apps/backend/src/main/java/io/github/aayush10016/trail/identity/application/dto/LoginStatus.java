package io.github.aayush10016.trail.identity.application.dto;

/**
 * Login result status.
 */
public enum LoginStatus {
    /**
     * Credentials were accepted and an access token was issued.
     */
    AUTHENTICATED,

    /**
     * Credentials were rejected with a generic response.
     */
    REJECTED
}
