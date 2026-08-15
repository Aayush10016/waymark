package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Command to consume an email verification token.
 *
 * @param token raw token presented by the user
 * @param requestContext identity request context
 */
public record ConsumeEmailVerificationCommand(String token, IdentityRequestContext requestContext) {

    /**
     * Creates a validated consume command.
     */
    public ConsumeEmailVerificationCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param token raw token presented by the user
     */
    public ConsumeEmailVerificationCommand(String token) {
        this(token, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "ConsumeEmailVerificationCommand[tokenPresent="
                + (token != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
