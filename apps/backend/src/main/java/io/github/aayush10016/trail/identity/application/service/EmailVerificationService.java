package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.ConsumeEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.dto.EmailVerificationResult;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application use case for email verification token request, resend, and
 * consumption.
 */
public final class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenGenerator tokenGenerator;
    private final EmailActionTokenHashingPolicy tokenHashingPolicy;
    private final EmailDeliveryPort emailDeliveryPort;
    private final IdentityEmailTemplateFactory emailTemplateFactory;
    private final IdentityRateLimiter rateLimiter;
    private final Clock clock;
    private final Supplier<UUID> tokenIdSupplier;
    private final Duration tokenTimeToLive;

    /**
     * Creates the email verification use case.
     *
     * @param tokenRepository token persistence port
     * @param tokenGenerator raw token generator
     * @param tokenHashingPolicy token hashing policy
     * @param emailDeliveryPort identity email delivery port
     * @param emailTemplateFactory identity email template factory
     * @param rateLimiter identity rate-limit port
     * @param clock application clock
     * @param tokenIdSupplier token ID supplier
     * @param tokenTimeToLive verification token lifetime
     */
    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            EmailVerificationTokenGenerator tokenGenerator,
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
     * Requests a verification token and returns a generic accepted result.
     *
     * @param command verification request command
     * @return generic accepted result
     */
    public EmailVerificationResult requestVerification(RequestEmailVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        var email = EmailAddress.from(command.email());
        var issuedAt = clock.instant();
        var attempt =
                IdentityRateLimitAttempt.forEmail(
                        IdentityRateLimitAction.EMAIL_VERIFICATION_REQUEST,
                        email,
                        command.requestContext(),
                        issuedAt);
        if (!rateLimiter.isAllowed(attempt)) {
            return EmailVerificationResult.accepted();
        }

        var rawToken = tokenGenerator.generateToken();
        var storedToken =
                new StoredEmailVerificationToken(
                        tokenIdSupplier.get(),
                        tokenHashingPolicy.hash(rawToken),
                        issuedAt,
                        issuedAt.plus(tokenTimeToLive));

        var storeResult = tokenRepository.storeForPendingEmail(email, storedToken);
        if (storeResult != EmailVerificationTokenRepository.StoreResult.STORED) {
            return EmailVerificationResult.accepted();
        }
        try {
            emailDeliveryPort.send(emailTemplateFactory.verificationEmail(email, rawToken));
        } catch (EmailDeliveryException ignored) {
            return EmailVerificationResult.accepted();
        }
        return EmailVerificationResult.accepted();
    }

    /**
     * Consumes a verification token and returns a generic accepted result.
     *
     * @param command token consumption command
     * @return generic accepted result
     */
    public EmailVerificationResult consumeVerification(ConsumeEmailVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        var consumedAt = clock.instant();
        var tokenHash = tokenHashingPolicy.hash(command.token());
        var attempt =
                new IdentityRateLimitAttempt(
                        IdentityRateLimitAction.EMAIL_VERIFICATION_CONSUME,
                        tokenHash.fingerprint(),
                        command.requestContext(),
                        consumedAt);
        if (!rateLimiter.isAllowed(attempt)) {
            return EmailVerificationResult.accepted();
        }

        tokenRepository.consume(tokenHash, consumedAt);
        return EmailVerificationResult.accepted();
    }

    private static Duration validateTimeToLive(Duration tokenTimeToLive) {
        Objects.requireNonNull(tokenTimeToLive, "tokenTimeToLive");
        if (tokenTimeToLive.isZero() || tokenTimeToLive.isNegative()) {
            throw new IllegalArgumentException("tokenTimeToLive must be positive");
        }
        return tokenTimeToLive;
    }
}
