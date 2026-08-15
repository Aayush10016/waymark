package io.github.aayush10016.trail.identity.application.command;

/**
 * Command for deleting the authenticated account after password reauthentication.
 *
 * @param password current account password
 */
public record DeleteAccountCommand(String password) {

    /**
     * Creates a validated command.
     */
    public DeleteAccountCommand {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }
}
