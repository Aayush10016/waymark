package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Command for requesting a password reset email.
 *
 * @param email user-provided account email
 * @param requestContext identity request context
 */
public record RequestPasswordResetCommand(String email, IdentityRequestContext requestContext) {

    /**
     * Creates a validated password-reset request command.
     */
    public RequestPasswordResetCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param email user-provided account email
     */
    public RequestPasswordResetCommand(String email) {
        this(email, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "RequestPasswordResetCommand[emailPresent="
                + (email != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
