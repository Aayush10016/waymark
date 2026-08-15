package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Command for consuming a password-reset token and setting a new password.
 *
 * @param token raw password-reset token
 * @param newPassword replacement raw password
 * @param requestContext identity request context
 */
public record ResetPasswordCommand(String token, String newPassword, IdentityRequestContext requestContext) {

    /**
     * Creates a validated password reset command.
     */
    public ResetPasswordCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param token raw password-reset token
     * @param newPassword replacement raw password
     */
    public ResetPasswordCommand(String token, String newPassword) {
        this(token, newPassword, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "ResetPasswordCommand[tokenPresent="
                + (token != null)
                + ", newPasswordPresent="
                + (newPassword != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
