package io.github.aayush10016.trail.identity.application.dto;

/**
 * Generic password-reset request result.
 *
 * <p>The response intentionally does not reveal whether an account exists,
 * whether the account is reset-eligible, whether a rate limit applied, or
 * whether email delivery succeeded.
 */
public record PasswordResetRequestResult() {

    private static final PasswordResetRequestResult ACCEPTED = new PasswordResetRequestResult();

    /**
     * Creates the generic accepted password-reset request result.
     *
     * @return accepted result
     */
    public static PasswordResetRequestResult accepted() {
        return ACCEPTED;
    }
}
