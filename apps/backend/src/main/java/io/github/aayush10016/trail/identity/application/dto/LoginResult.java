package io.github.aayush10016.trail.identity.application.dto;

import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic login result. Rejected results intentionally do not reveal whether an
 * account exists or why authentication failed.
 *
 * @param status login status
 * @param accessToken optional issued access token
 * @param refreshToken optional issued refresh credential
 */
public record LoginResult(LoginStatus status, IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {

    private static final LoginResult REJECTED = new LoginResult(LoginStatus.REJECTED, null, null);

    /**
     * Creates a validated login result.
     */
    public LoginResult {
        Objects.requireNonNull(status, "status");
        if (status == LoginStatus.AUTHENTICATED && (accessToken == null || refreshToken == null)) {
            throw new IllegalArgumentException("authenticated login requires access and refresh tokens");
        }
        if (status == LoginStatus.REJECTED && (accessToken != null || refreshToken != null)) {
            throw new IllegalArgumentException("rejected login must not include tokens");
        }
    }

    /**
     * Creates an authenticated login result.
     *
     * @param accessToken issued access token
     * @param refreshToken issued refresh credential
     * @return authenticated result
     */
    public static LoginResult authenticated(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
        return new LoginResult(LoginStatus.AUTHENTICATED, accessToken, refreshToken);
    }

    /**
     * Creates a generic rejected login result.
     *
     * @return rejected result
     */
    public static LoginResult rejected() {
        return REJECTED;
    }

    /**
     * Returns the optional access token.
     *
     * @return optional access token
     */
    public Optional<IssuedAccessToken> accessTokenOptional() {
        return Optional.ofNullable(accessToken);
    }

    /**
     * Returns the optional issued refresh credential.
     *
     * @return optional refresh credential
     */
    public Optional<IssuedRefreshToken> refreshTokenOptional() {
        return Optional.ofNullable(refreshToken);
    }

    @Override
    public String toString() {
        return "LoginResult[status="
                + status
                + ", hasAccessToken="
                + (accessToken != null)
                + ", hasRefreshToken="
                + (refreshToken != null)
                + "]";
    }
}
