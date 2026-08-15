package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetRequestResult;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application use case for requesting a password reset token.
 */
public final class PasswordResetRequestService {

    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final EmailActionTokenHashingPolicy tokenHashingPolicy;
    private final EmailDeliveryPort emailDeliveryPort;
    private final IdentityEmailTemplateFactory emailTemplateFactory;
    private final IdentityRateLimiter rateLimiter;
    private final Clock clock;
    private final Supplier<UUID> tokenIdSupplier;
    private final Duration tokenTimeToLive;

    /**
     * Creates the password-reset request use case.
     *
     * @param tokenRepository reset-token persistence port
     * @param tokenGenerator raw reset-token generator
     * @param tokenHashingPolicy reset-token hashing policy
     * @param emailDeliveryPort identity email delivery port
     * @param emailTemplateFactory identity email template factory
     * @param rateLimiter identity rate-limit port
     * @param clock application clock
     * @param tokenIdSupplier token ID supplier
     * @param tokenTimeToLive reset token lifetime
     */
    public PasswordResetRequestService(
            PasswordResetTokenRepository tokenRepository,
            PasswordResetTokenGenerator tokenGenerator,
            EmailActionTokenHashingPolicy tokenHashingPolicy,
            EmailDeliveryPort emailDeliveryPort,
            IdentityEmailTemplateFactory emailTemplateFactory,
            IdentityRateLimiter rateLimiter,
            Clock clock,
            Supplier<UUID> tokenIdSupplier,
            Duration tokenTimeToLive) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.tokenHashingPolicy = Objects.requireNonNull(tokenHashingPolicy, "tokenHashingPolicy");
        this.emailDeliveryPort = Objects.requireNonNull(emailDeliveryPort, "emailDeliveryPort");
        this.emailTemplateFactory = Objects.requireNonNull(emailTemplateFactory, "emailTemplateFactory");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenIdSupplier = Objects.requireNonNull(tokenIdSupplier, "tokenIdSupplier");
        this.tokenTimeToLive = validateTimeToLive(tokenTimeToLive);
    }

    /**
     * Requests a password reset and always returns a generic accepted result.
     *
     * @param command password-reset request command
     * @return generic accepted result
     */
    public PasswordResetRequestResult requestReset(RequestPasswordResetCommand command) {
        Objects.requireNonNull(command, "command");
        var email = EmailAddress.from(command.email());
        var requestedAt = clock.instant();
        var attempt =
                IdentityRateLimitAttempt.forEmail(
                        IdentityRateLimitAction.PASSWORD_RESET_REQUEST,
                        email,
                        command.requestContext(),
                        requestedAt);
        if (!rateLimiter.isAllowed(attempt)) {
            return PasswordResetRequestResult.accepted();
        }

        var rawToken = tokenGenerator.generateToken();
        var storedToken =
                new StoredPasswordResetToken(
                        tokenIdSupplier.get(),
                        tokenHashingPolicy.hash(rawToken),
                        requestedAt,
                        requestedAt.plus(tokenTimeToLive));
        var storeResult = tokenRepository.storeForActiveEmail(email, storedToken);
        if (storeResult.status() != PasswordResetTokenRepository.StoreStatus.STORED) {
            return PasswordResetRequestResult.accepted();
        }

        try {
            emailDeliveryPort.send(emailTemplateFactory.passwordResetEmail(storeResult.recipient(), rawToken));
        } catch (EmailDeliveryException ignored) {
            return PasswordResetRequestResult.accepted();
        }
        return PasswordResetRequestResult.accepted();
    }

    private static Duration validateTimeToLive(Duration tokenTimeToLive) {
        Objects.requireNonNull(tokenTimeToLive, "tokenTimeToLive");
        if (tokenTimeToLive.isZero() || tokenTimeToLive.isNegative()) {
            throw new IllegalArgumentException("tokenTimeToLive must be positive");
        }
        return tokenTimeToLive;
    }
}
