package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.RotateRefreshSessionCommand;
import io.github.aayush10016.trail.identity.application.command.RevokeCurrentSessionCommand;
import io.github.aayush10016.trail.identity.application.dto.CurrentSessionRevocationResult;
import io.github.aayush10016.trail.identity.application.dto.RefreshSessionResult;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenHashingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application use case for refresh-session creation and rotation.
 */
public final class RefreshSessionService implements RefreshSessionCreator {

    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHashingPolicy refreshTokenHashingPolicy;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;
    private final Supplier<UUID> sessionIdSupplier;
    private final Supplier<UUID> sessionFamilyIdSupplier;
    private final Duration refreshTokenTimeToLive;

    /**
     * Creates the refresh-session use case.
     *
     * @param refreshSessionRepository refresh-session persistence port
     * @param refreshTokenGenerator raw refresh token generator
     * @param refreshTokenHashingPolicy refresh token hashing policy
     * @param accessTokenIssuer access-token issuer
     * @param clock application clock
     * @param sessionIdSupplier auth-session ID supplier
     * @param sessionFamilyIdSupplier session-family ID supplier
     * @param refreshTokenTimeToLive refresh credential lifetime
     */
    public RefreshSessionService(
            RefreshSessionRepository refreshSessionRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHashingPolicy refreshTokenHashingPolicy,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock,
            Supplier<UUID> sessionIdSupplier,
            Supplier<UUID> sessionFamilyIdSupplier,
            Duration refreshTokenTimeToLive) {
        this.refreshSessionRepository = Objects.requireNonNull(refreshSessionRepository, "refreshSessionRepository");
        this.refreshTokenGenerator = Objects.requireNonNull(refreshTokenGenerator, "refreshTokenGenerator");
        this.refreshTokenHashingPolicy =
                Objects.requireNonNull(refreshTokenHashingPolicy, "refreshTokenHashingPolicy");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionIdSupplier = Objects.requireNonNull(sessionIdSupplier, "sessionIdSupplier");
        this.sessionFamilyIdSupplier = Objects.requireNonNull(sessionFamilyIdSupplier, "sessionFamilyIdSupplier");
        this.refreshTokenTimeToLive = validateTimeToLive(refreshTokenTimeToLive);
    }

    /**
     * Creates an initial refresh session for a freshly authenticated principal.
     *
     * @param principal authenticated principal
     * @return raw refresh credential returned once to the client
     */
    @Override
    public IssuedRefreshToken createSession(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(refreshTokenTimeToLive);
        var rawToken = refreshTokenGenerator.generateToken();
        var session =
                new StoredRefreshSession(
                        sessionIdSupplier.get(),
                        sessionFamilyIdSupplier.get(),
                        principal,
                        refreshTokenHashingPolicy.hash(rawToken),
                        0,
                        issuedAt,
                        expiresAt);

        refreshSessionRepository.create(session);
        return IssuedRefreshToken.bearer(rawToken, expiresAt);
    }

    /**
     * Rotates a presented refresh credential and issues new access/refresh
     * credentials.
     *
     * @param command refresh rotation command
     * @return rotated credentials or generic rejection
     */
    public RefreshSessionResult rotate(RotateRefreshSessionCommand command) {
        Objects.requireNonNull(command, "command");
        var rotatedAt = clock.instant();
        var newRawToken = refreshTokenGenerator.generateToken();
        var newExpiresAt = rotatedAt.plus(refreshTokenTimeToLive);
        var replacement =
                new RefreshSessionRepository.RefreshSessionReplacement(
                        sessionIdSupplier.get(),
                        refreshTokenHashingPolicy.hash(newRawToken),
                        newExpiresAt);
        var rotation =
                refreshSessionRepository.rotate(
                        refreshTokenHashingPolicy.hash(command.refreshToken()), replacement, rotatedAt);

        if (rotation.status() != RefreshSessionRepository.RotationStatus.ROTATED) {
            return RefreshSessionResult.rejected();
        }

        return RefreshSessionResult.rotated(
                accessTokenIssuer.issue(rotation.principal()),
                IssuedRefreshToken.bearer(newRawToken, newExpiresAt));
    }

    /**
     * Revokes the family associated with the current refresh credential.
     *
     * @param command current-session revocation command
     * @return generic completed result
     */
    public CurrentSessionRevocationResult revokeCurrentSession(RevokeCurrentSessionCommand command) {
        Objects.requireNonNull(command, "command");
        refreshSessionRepository.revokeCurrentFamily(
                refreshTokenHashingPolicy.hash(command.refreshToken()), clock.instant());
        return CurrentSessionRevocationResult.completed();
    }

    private static Duration validateTimeToLive(Duration refreshTokenTimeToLive) {
        Objects.requireNonNull(refreshTokenTimeToLive, "refreshTokenTimeToLive");
        if (refreshTokenTimeToLive.isZero() || refreshTokenTimeToLive.isNegative()) {
            throw new IllegalArgumentException("refreshTokenTimeToLive must be positive");
        }
        return refreshTokenTimeToLive;
    }
}
