package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Command to request or resend email verification.
 *
 * @param email requested account email
 * @param requestContext identity request context
 */
public record RequestEmailVerificationCommand(String email, IdentityRequestContext requestContext) {

    /**
     * Creates a validated request command.
     */
    public RequestEmailVerificationCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param email requested account email
     */
    public RequestEmailVerificationCommand(String email) {
        this(email, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "RequestEmailVerificationCommand[emailPresent="
                + (email != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
