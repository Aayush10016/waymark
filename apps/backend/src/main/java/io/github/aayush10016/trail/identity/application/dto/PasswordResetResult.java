package io.github.aayush10016.trail.identity.application.dto;

/**
 * Generic password-reset result.
 *
 * <p>The result intentionally does not reveal whether the reset token was
 * active, expired, unknown, or already consumed.
 */
public record PasswordResetResult() {

    private static final PasswordResetResult ACCEPTED = new PasswordResetResult();

    /**
     * Creates the generic accepted password-reset result.
     *
     * @return accepted result
     */
    public static PasswordResetResult accepted() {
        return ACCEPTED;
    }
}
