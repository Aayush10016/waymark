package io.github.aayush10016.trail.identity.application.command;

/**
 * Command for revoking the current refresh-session family.
 *
 * @param refreshToken current opaque refresh credential presented by the client
 */
public record RevokeCurrentSessionCommand(String refreshToken) {

    /**
     * Creates a validated current-session revocation command.
     */
    public RevokeCurrentSessionCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RevokeCurrentSessionCommand[refreshTokenPresent=" + (refreshToken != null) + "]";
    }
}
