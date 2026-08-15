package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.application.command.RegisterUserCommand;
import io.github.aayush10016.trail.identity.application.dto.RegistrationResult;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.RegisteredUser;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.PasswordHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.UserRegistrationRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application use case for registering an unverified email/password account.
 */
public final class RegisterUserService {

    private final UserRegistrationRepository userRegistrationRepository;
    private final PasswordHashingPolicy passwordHashingPolicy;
    private final IdentityRateLimiter rateLimiter;
    private final Clock clock;
    private final Supplier<UUID> userIdSupplier;

    /**
     * Creates the registration use case.
     *
     * @param userRegistrationRepository user storage port
     * @param passwordPolicy password hashing policy
     * @param rateLimiter identity rate-limit port
     * @param clock application clock
     * @param userIdSupplier user ID supplier
     */
    public RegisterUserService(
            UserRegistrationRepository userRegistrationRepository,
            PasswordHashingPolicy passwordHashingPolicy,
            IdentityRateLimiter rateLimiter,
            Clock clock,
            Supplier<UUID> userIdSupplier) {
        this.userRegistrationRepository = Objects.requireNonNull(userRegistrationRepository, "userRegistrationRepository");
        this.passwordHashingPolicy = Objects.requireNonNull(passwordHashingPolicy, "passwordHashingPolicy");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.userIdSupplier = Objects.requireNonNull(userIdSupplier, "userIdSupplier");
    }

    /**
     * Registers a user or returns the same generic result when the normalized
     * email is already registered.
     *
     * @param command registration command
     * @return generic registration result
     */
    public RegistrationResult register(RegisterUserCommand command) {
        Objects.requireNonNull(command, "command");

        var email = EmailAddress.from(command.email());
        var registeredAt = clock.instant();
        var attempt =
                IdentityRateLimitAttempt.forEmail(
                        IdentityRateLimitAction.REGISTRATION,
                        email,
                        command.requestContext(),
                        registeredAt);
        if (!rateLimiter.isAllowed(attempt)) {
            return RegistrationResult.accepted();
        }

        if (!passwordHashingPolicy.isWithinMaximumInputSize(command.password())) {
            throw new IllegalArgumentException("password exceeds maximum input size");
        }

        var passwordHash = passwordHashingPolicy.hash(command.password());
        var user =
                RegisteredUser.pendingVerification(
                        userIdSupplier.get(), email, passwordHash, registeredAt);

        userRegistrationRepository.store(user);
        return RegistrationResult.accepted();
    }
}
