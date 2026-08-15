package io.github.aayush10016.trail.identity.application.command;

import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import java.util.Objects;

/**
 * Command to register an unverified email/password account.
 *
 * @param email user-provided email
 * @param password raw password
 * @param requestContext identity request context
 */
public record RegisterUserCommand(String email, CharSequence password, IdentityRequestContext requestContext) {

    /**
     * Creates a validated registration command.
     */
    public RegisterUserCommand {
        Objects.requireNonNull(requestContext, "requestContext");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }

    /**
     * Creates a command without HTTP source context.
     *
     * @param email user-provided email
     * @param password raw password
     */
    public RegisterUserCommand(String email, CharSequence password) {
        this(email, password, IdentityRequestContext.unspecified());
    }

    @Override
    public String toString() {
        return "RegisterUserCommand[emailPresent="
                + (email != null)
                + ", passwordPresent="
                + (password != null)
                + ", requestContext="
                + requestContext
                + "]";
    }
}
