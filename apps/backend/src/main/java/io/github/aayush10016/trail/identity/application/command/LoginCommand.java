package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Login command carrying email/password credentials.
 *
 * @param email user-provided email
 * @param password user-provided password
 * @param requestContext identity request context
 */
public record LoginCommand(String email, String password, IdentityRequestContext requestContext) {

    /**
     * Creates a validated login command.
     */
    public LoginCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param email user-provided email
     * @param password user-provided password
     */
    public LoginCommand(String email, String password) {
        this(email, password, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "LoginCommand[emailPresent="
                + (email != null)
                + ", passwordPresent="
                + (password != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
