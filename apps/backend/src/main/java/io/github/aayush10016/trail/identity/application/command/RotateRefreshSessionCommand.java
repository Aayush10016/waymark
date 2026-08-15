package io.github.aayush10016.trail.identity.application.command;

/**
 * Command to rotate an opaque refresh credential.
 *
 * @param refreshToken raw refresh token presented by the client
 */
public record RotateRefreshSessionCommand(String refreshToken) {

    /**
     * Creates a validated refresh rotation command.
     */
    public RotateRefreshSessionCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RotateRefreshSessionCommand[refreshTokenPresent=" + (refreshToken != null) + "]";
    }
}
