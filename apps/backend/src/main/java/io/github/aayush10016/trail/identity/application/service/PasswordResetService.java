package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.ResetPasswordCommand;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetResult;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.PasswordHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Application use case for consuming a password-reset token and revoking
 * existing refresh-session families.
 */
public final class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final EmailActionTokenHashingPolicy tokenHashingPolicy;
    private final PasswordHashingPolicy passwordHashingPolicy;
    private final IdentityRateLimiter rateLimiter;
    private final Clock clock;

    /**
     * Creates the password reset use case.
     *
     * @param tokenRepository reset-token persistence port
     * @param refreshSessionRepository refresh-session persistence port
     * @param tokenHashingPolicy reset-token hashing policy
     * @param passwordHashingPolicy password hashing policy
     * @param rateLimiter identity rate-limit port
     * @param clock application clock
     */
    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            RefreshSessionRepository refreshSessionRepository,
            EmailActionTokenHashingPolicy tokenHashingPolicy,
            PasswordHashingPolicy passwordHashingPolicy,
            IdentityRateLimiter rateLimiter,
            Clock clock) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.refreshSessionRepository = Objects.requireNonNull(refreshSessionRepository, "refreshSessionRepository");
        this.tokenHashingPolicy = Objects.requireNonNull(tokenHashingPolicy, "tokenHashingPolicy");
        this.passwordHashingPolicy = Objects.requireNonNull(passwordHashingPolicy, "passwordHashingPolicy");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Consumes a password reset token and revokes all refresh-session families
     * for the reset account.
     *
     * @param command password reset command
     * @return generic accepted result
     */
    public PasswordResetResult resetPassword(ResetPasswordCommand command) {
        Objects.requireNonNull(command, "command");
        if (!passwordHashingPolicy.isWithinMaximumInputSize(command.newPassword())) {
            throw new IllegalArgumentException("newPassword exceeds maximum input size");
        }

        var resetAt = clock.instant();
        var tokenHash = tokenHashingPolicy.hash(command.token());
        var attempt =
                new IdentityRateLimitAttempt(
                        IdentityRateLimitAction.PASSWORD_RESET_CONSUME,
                        tokenHash.fingerprint(),
                        command.requestContext(),
                        resetAt);
        if (!rateLimiter.isAllowed(attempt)) {
            return PasswordResetResult.accepted();
        }

        var result =
                tokenRepository.consume(
                        tokenHash,
                        passwordHashingPolicy.hash(command.newPassword()),
                        resetAt);
        if (result.status() == PasswordResetTokenRepository.ConsumeStatus.CONSUMED) {
            refreshSessionRepository.revokeAllFamiliesForUser(result.principal(), resetAt);
        }
        return PasswordResetResult.accepted();
    }
}
