package io.github.aayush10016.trail.identity.application.dto;

/**
 * Generic result for current-session revocation.
 *
 * <p>The result intentionally does not reveal whether the presented refresh
 * credential was active, expired, unknown, or already revoked.
 */
public record CurrentSessionRevocationResult() {

    private static final CurrentSessionRevocationResult COMPLETED = new CurrentSessionRevocationResult();

    /**
     * Creates the generic completed revocation result.
     *
     * @return completed revocation result
     */
    public static CurrentSessionRevocationResult completed() {
        return COMPLETED;
    }
}
