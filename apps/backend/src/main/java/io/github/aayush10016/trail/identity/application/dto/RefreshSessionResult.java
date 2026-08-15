package io.github.aayush10016.trail.identity.application.dto;

import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of refresh-session rotation. Rejected results intentionally do not
 * reveal whether the presented credential was unknown, expired, already used, or
 * compromised.
 *
 * @param status refresh-session status
 * @param accessToken optional issued access token
 * @param refreshToken optional rotated refresh credential
 */
public record RefreshSessionResult(
        RefreshSessionStatus status, IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {

    private static final RefreshSessionResult REJECTED =
            new RefreshSessionResult(RefreshSessionStatus.REJECTED, null, null);

    /**
     * Creates a validated refresh-session result.
     */
    public RefreshSessionResult {
        Objects.requireNonNull(status, "status");
        if (status == RefreshSessionStatus.ROTATED && (accessToken == null || refreshToken == null)) {
            throw new IllegalArgumentException("rotated refresh result requires access and refresh tokens");
        }
        if (status == RefreshSessionStatus.REJECTED && (accessToken != null || refreshToken != null)) {
            throw new IllegalArgumentException("rejected refresh result must not include tokens");
        }
    }

    /**
     * Creates a rotated refresh-session result.
     *
     * @param accessToken issued access token
     * @param refreshToken rotated refresh credential
     * @return rotated result
     */
    public static RefreshSessionResult rotated(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
        return new RefreshSessionResult(RefreshSessionStatus.ROTATED, accessToken, refreshToken);
    }

    /**
     * Creates a generic rejected refresh-session result.
     *
     * @return rejected result
     */
    public static RefreshSessionResult rejected() {
        return REJECTED;
    }

    /**
     * Returns the optional issued access token.
     *
     * @return optional access token
     */
    public Optional<IssuedAccessToken> accessTokenOptional() {
        return Optional.ofNullable(accessToken);
    }

    /**
     * Returns the optional rotated refresh credential.
     *
     * @return optional refresh credential
     */
    public Optional<IssuedRefreshToken> refreshTokenOptional() {
        return Optional.ofNullable(refreshToken);
    }

    @Override
    public String toString() {
        return "RefreshSessionResult[status="
                + status
                + ", hasAccessToken="
                + (accessToken != null)
                + ", hasRefreshToken="
                + (refreshToken != null)
                + "]";
    }
}
