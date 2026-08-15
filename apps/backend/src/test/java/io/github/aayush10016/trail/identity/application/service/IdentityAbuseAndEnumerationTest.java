package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.application.command.ConsumeEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.LoginCommand;
import io.github.aayush10016.trail.identity.application.command.RegisterUserCommand;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.command.ResetPasswordCommand;
import io.github.aayush10016.trail.identity.application.dto.EmailVerificationResult;
import io.github.aayush10016.trail.identity.application.dto.LoginResult;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetRequestResult;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetResult;
import io.github.aayush10016.trail.identity.application.dto.RegistrationResult;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAction;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.IdentityRequestContext;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import io.github.aayush10016.trail.identity.domain.model.LoginAccount;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;
import io.github.aayush10016.trail.identity.domain.model.RegisteredUser;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.LoginUserRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import io.github.aayush10016.trail.identity.domain.port.UserRegistrationRepository;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityAbuseAndEnumerationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final IdentityRequestContext REQUEST_CONTEXT = new IdentityRequestContext("203.0.113.42");
    private static final UUID USER_ID = UUID.fromString("07032c8a-0d2c-40a2-a862-a7d2e87a91dd");
    private static final UUID TOKEN_ID = UUID.fromString("eec8a2dc-9d86-4f2e-9b16-3ec3867ef423");
    private static final PasswordHashRecord PASSWORD_HASH =
            new PasswordHashRecord("hash", "TEST", "test-parameters", 1);
    private static final EmailActionTokenHashingPolicy TOKEN_HASHING_POLICY = new Sha256EmailActionTokenHashingPolicy();

    @Test
    void registrationThrottleUsesIpAndAccountKeyAndKeepsGenericResult() {
        var rateLimiter = new CapturingIdentityRateLimiter(false);
        var repository = new CapturingRegistrationRepository();
        var passwordPolicy = new CapturingPasswordPolicy();
        var service =
                new RegisterUserService(
                        repository,
                        passwordPolicy,
                        rateLimiter,
                        CLOCK,
                        () -> USER_ID);

        var result =
                service.register(
                        new RegisterUserCommand(
                                " Developer@Example.COM ",
                                "correct horse battery staple",
                                REQUEST_CONTEXT));

        assertThat(result).isEqualTo(RegistrationResult.accepted());
        assertThat(rateLimiter.attempts())
                .containsExactly(
                        new IdentityRateLimitAttempt(
                                IdentityRateLimitAction.REGISTRATION,
                                "developer@example.com",
                                REQUEST_CONTEXT,
                                NOW));
        assertThat(passwordPolicy.hashCount()).isZero();
        assertThat(repository.storedUsers()).isEmpty();
    }

    @Test
    void loginThrottleUsesIpAndAccountKeyAndKeepsGenericRejectedResult() {
        var rateLimiter = new CapturingIdentityRateLimiter(false);
        var repository = new CapturingLoginRepository();
        var passwordPolicy = new CapturingPasswordPolicy();
        var service =
                new LoginUserService(
                        repository,
                        passwordPolicy,
                        principal -> IssuedAccessToken.bearer("access", NOW.plusSeconds(300)),
                        principal -> IssuedRefreshToken.bearer("refresh", NOW.plus(Duration.ofDays(30))),
                        rateLimiter,
                        CLOCK);

        var result = service.login(new LoginCommand("Developer@Example.COM", "correct-password", REQUEST_CONTEXT));

        assertThat(result).isEqualTo(LoginResult.rejected());
        assertThat(rateLimiter.attempts())
                .containsExactly(
                        new IdentityRateLimitAttempt(
                                IdentityRateLimitAction.LOGIN,
                                "developer@example.com",
                                REQUEST_CONTEXT,
                                NOW));
        assertThat(repository.lookupCount()).isZero();
        assertThat(passwordPolicy.matchCount()).isZero();
    }

    @Test
    void verificationThrottleCoversRequestAndConsumeWithoutLeakingState() {
        var rateLimiter = new CapturingIdentityRateLimiter(false);
        var generator = new CountingVerificationTokenGenerator();
        var repository = new CapturingVerificationRepository();
        var service =
                new EmailVerificationService(
                        repository,
                        generator,
                        TOKEN_HASHING_POLICY,
                        new CapturingEmailDeliveryPort(),
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        rateLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofHours(2));

        var requestResult =
                service.requestVerification(
                        new RequestEmailVerificationCommand("Developer@Example.COM", REQUEST_CONTEXT));
        var consumeResult =
                service.consumeVerification(
                        new ConsumeEmailVerificationCommand("raw-verification-token", REQUEST_CONTEXT));

        assertThat(requestResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(consumeResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(generator.generatedCount()).isZero();
        assertThat(repository.storeCount()).isZero();
        assertThat(repository.consumeCount()).isZero();
        assertThat(rateLimiter.attempts())
                .extracting(IdentityRateLimitAttempt::action)
                .containsExactly(
                        IdentityRateLimitAction.EMAIL_VERIFICATION_REQUEST,
                        IdentityRateLimitAction.EMAIL_VERIFICATION_CONSUME);
        assertThat(rateLimiter.attempts().get(0).accountKey()).isEqualTo("developer@example.com");
        assertThat(rateLimiter.attempts().get(1).accountKey()).doesNotContain("raw-verification-token");
        assertThat(rateLimiter.attempts().get(1).requestContext()).isEqualTo(REQUEST_CONTEXT);
    }

    @Test
    void passwordResetThrottleCoversRequestAndConsumeWithoutLeakingState() {
        var rateLimiter = new CapturingIdentityRateLimiter(false);
        var generator = new CountingPasswordResetTokenGenerator();
        var resetRepository = new CapturingPasswordResetRepository();
        var refreshRepository = new CapturingRefreshRepository();
        var emailDelivery = new CapturingEmailDeliveryPort();
        var service =
                new PasswordResetRequestService(
                        resetRepository,
                        generator,
                        TOKEN_HASHING_POLICY,
                        emailDelivery,
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        rateLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofMinutes(30));
        var resetService =
                new PasswordResetService(
                        resetRepository,
                        refreshRepository,
                        TOKEN_HASHING_POLICY,
                        new CapturingPasswordPolicy(),
                        rateLimiter,
                        CLOCK);

        var requestResult =
                service.requestReset(new RequestPasswordResetCommand("Developer@Example.COM", REQUEST_CONTEXT));
        var consumeResult =
                resetService.resetPassword(
                        new ResetPasswordCommand("raw-reset-token", "new secure password", REQUEST_CONTEXT));

        assertThat(requestResult).isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(consumeResult).isEqualTo(PasswordResetResult.accepted());
        assertThat(generator.generatedCount()).isZero();
        assertThat(resetRepository.storeCount()).isZero();
        assertThat(resetRepository.consumeCount()).isZero();
        assertThat(emailDelivery.messages()).isEmpty();
        assertThat(refreshRepository.revokedCount()).isZero();
        assertThat(rateLimiter.attempts())
                .extracting(IdentityRateLimitAttempt::action)
                .containsExactly(
                        IdentityRateLimitAction.PASSWORD_RESET_REQUEST,
                        IdentityRateLimitAction.PASSWORD_RESET_CONSUME);
        assertThat(rateLimiter.attempts().get(0).accountKey()).isEqualTo("developer@example.com");
        assertThat(rateLimiter.attempts().get(1).accountKey()).doesNotContain("raw-reset-token");
    }

    @Test
    void enumerationOutcomesRemainStableAcrossExistingMissingInvalidAndThrottledAccounts() {
        var allowingLimiter = new CapturingIdentityRateLimiter(true);
        var denyingLimiter = new CapturingIdentityRateLimiter(false);

        var activeLoginRepository = new CapturingLoginRepository();
        activeLoginRepository.add(activeAccount("developer@example.com"));
        var passwordPolicy = new CapturingPasswordPolicy();
        passwordPolicy.accept("correct-password", PASSWORD_HASH);
        var allowingLogin =
                new LoginUserService(
                        activeLoginRepository,
                        passwordPolicy,
                        principal -> IssuedAccessToken.bearer("access", NOW.plusSeconds(300)),
                        principal -> IssuedRefreshToken.bearer("refresh", NOW.plus(Duration.ofDays(30))),
                        allowingLimiter,
                        CLOCK);
        var throttledLogin =
                new LoginUserService(
                        activeLoginRepository,
                        passwordPolicy,
                        principal -> IssuedAccessToken.bearer("access", NOW.plusSeconds(300)),
                        principal -> IssuedRefreshToken.bearer("refresh", NOW.plus(Duration.ofDays(30))),
                        denyingLimiter,
                        CLOCK);

        assertThat(allowingLogin.login(new LoginCommand("missing@example.com", "correct-password")))
                .isEqualTo(LoginResult.rejected());
        assertThat(allowingLogin.login(new LoginCommand("developer@example.com", "wrong-password")))
                .isEqualTo(LoginResult.rejected());
        assertThat(throttledLogin.login(new LoginCommand("developer@example.com", "correct-password")))
                .isEqualTo(LoginResult.rejected());

        var verificationService =
                new EmailVerificationService(
                        new CapturingVerificationRepository(),
                        new CountingVerificationTokenGenerator(),
                        TOKEN_HASHING_POLICY,
                        new CapturingEmailDeliveryPort(),
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        allowingLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofHours(2));
        var throttledVerificationService =
                new EmailVerificationService(
                        new CapturingVerificationRepository(),
                        new CountingVerificationTokenGenerator(),
                        TOKEN_HASHING_POLICY,
                        new CapturingEmailDeliveryPort(),
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        denyingLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofHours(2));

        assertThat(verificationService.requestVerification(new RequestEmailVerificationCommand("missing@example.com")))
                .isEqualTo(EmailVerificationResult.accepted());
        assertThat(throttledVerificationService.requestVerification(new RequestEmailVerificationCommand("missing@example.com")))
                .isEqualTo(EmailVerificationResult.accepted());
        assertThat(verificationService.consumeVerification(new ConsumeEmailVerificationCommand("unknown-token")))
                .isEqualTo(EmailVerificationResult.accepted());

        var resetRepository = new CapturingPasswordResetRepository();
        var resetRequestService =
                new PasswordResetRequestService(
                        resetRepository,
                        new CountingPasswordResetTokenGenerator(),
                        TOKEN_HASHING_POLICY,
                        new CapturingEmailDeliveryPort(),
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        allowingLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofMinutes(30));
        var throttledResetRequestService =
                new PasswordResetRequestService(
                        resetRepository,
                        new CountingPasswordResetTokenGenerator(),
                        TOKEN_HASHING_POLICY,
                        new CapturingEmailDeliveryPort(),
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify"),
                                URI.create("https://app.trail.test/reset")),
                        denyingLimiter,
                        CLOCK,
                        () -> TOKEN_ID,
                        Duration.ofMinutes(30));

        assertThat(resetRequestService.requestReset(new RequestPasswordResetCommand("missing@example.com")))
                .isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(throttledResetRequestService.requestReset(new RequestPasswordResetCommand("missing@example.com")))
                .isEqualTo(PasswordResetRequestResult.accepted());
    }

    private static LoginAccount activeAccount(String email) {
        return new LoginAccount(
                USER_ID,
                EmailAddress.from(email),
                PASSWORD_HASH,
                UserStatus.ACTIVE,
                NOW.minus(Duration.ofDays(1)));
    }

    private static final class CapturingIdentityRateLimiter implements IdentityRateLimiter {

        private final boolean allowed;
        private final List<IdentityRateLimitAttempt> attempts = new ArrayList<>();

        private CapturingIdentityRateLimiter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean isAllowed(IdentityRateLimitAttempt attempt) {
            attempts.add(attempt);
            return allowed;
        }

        List<IdentityRateLimitAttempt> attempts() {
            return List.copyOf(attempts);
        }
    }

    private static final class CapturingPasswordPolicy implements PasswordHashingPolicy, PasswordVerificationPolicy {

        private final Map<String, PasswordHashRecord> acceptedPasswords = new HashMap<>();
        private int hashCount;
        private int matchCount;

        @Override
        public boolean isWithinMaximumInputSize(CharSequence rawPassword) {
            return true;
        }

        @Override
        public PasswordHashRecord hash(CharSequence rawPassword) {
            hashCount++;
            return PASSWORD_HASH;
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHashRecord passwordHash) {
            matchCount++;
            return passwordHash.equals(acceptedPasswords.get(rawPassword.toString()));
        }

        @Override
        public boolean requiresRehash(PasswordHashRecord passwordHash) {
            return false;
        }

        void accept(String rawPassword, PasswordHashRecord passwordHash) {
            acceptedPasswords.put(rawPassword, passwordHash);
        }

        int hashCount() {
            return hashCount;
        }

        int matchCount() {
            return matchCount;
        }
    }

    private static final class CapturingRegistrationRepository implements UserRegistrationRepository {

        private final List<RegisteredUser> storedUsers = new ArrayList<>();

        @Override
        public StoreResult store(RegisteredUser user) {
            storedUsers.add(user);
            return StoreResult.CREATED;
        }

        List<RegisteredUser> storedUsers() {
            return List.copyOf(storedUsers);
        }
    }

    private static final class CapturingLoginRepository implements LoginUserRepository {

        private final Map<String, LoginAccount> accounts = new HashMap<>();
        private int lookupCount;

        @Override
        public Optional<LoginAccount> findByEmail(EmailAddress email) {
            lookupCount++;
            return Optional.ofNullable(accounts.get(email.normalized()));
        }

        @Override
        public void updatePasswordHash(UUID userId, PasswordHashRecord passwordHash) {}

        void add(LoginAccount account) {
            accounts.put(account.email().normalized(), account);
        }

        int lookupCount() {
            return lookupCount;
        }
    }

    private static final class CountingVerificationTokenGenerator implements EmailVerificationTokenGenerator {

        private int generatedCount;

        @Override
        public String generateToken() {
            generatedCount++;
            return "raw-verification-token";
        }

        int generatedCount() {
            return generatedCount;
        }
    }

    private static final class CapturingVerificationRepository implements EmailVerificationTokenRepository {

        private int storeCount;
        private int consumeCount;

        @Override
        public StoreResult storeForPendingEmail(EmailAddress email, StoredEmailVerificationToken token) {
            storeCount++;
            return StoreResult.NO_PENDING_USER;
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, Instant consumedAt) {
            consumeCount++;
            return ConsumeResult.NOT_ACTIVE;
        }

        int storeCount() {
            return storeCount;
        }

        int consumeCount() {
            return consumeCount;
        }
    }

    private static final class CountingPasswordResetTokenGenerator implements PasswordResetTokenGenerator {

        private int generatedCount;

        @Override
        public String generateToken() {
            generatedCount++;
            return "raw-reset-token";
        }

        int generatedCount() {
            return generatedCount;
        }
    }

    private static final class CapturingPasswordResetRepository implements PasswordResetTokenRepository {

        private int storeCount;
        private int consumeCount;

        @Override
        public StoreResult storeForActiveEmail(EmailAddress email, StoredPasswordResetToken token) {
            storeCount++;
            return StoreResult.noActiveUser();
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, PasswordHashRecord newPasswordHash, Instant consumedAt) {
            consumeCount++;
            return ConsumeResult.notActive();
        }

        int storeCount() {
            return storeCount;
        }

        int consumeCount() {
            return consumeCount;
        }
    }

    private static final class CapturingEmailDeliveryPort implements EmailDeliveryPort {

        private final List<EmailDeliveryMessage> messages = new ArrayList<>();

        @Override
        public void send(EmailDeliveryMessage message) {
            messages.add(message);
        }

        List<EmailDeliveryMessage> messages() {
            return List.copyOf(messages);
        }
    }

    private static final class CapturingRefreshRepository implements RefreshSessionRepository {

        private int revokedCount;

        @Override
        public void create(StoredRefreshSession session) {}

        @Override
        public RotationResult rotate(
                RefreshTokenHash presentedTokenHash,
                RefreshSessionReplacement replacement,
                Instant rotatedAt) {
            return RotationResult.notActive();
        }

        @Override
        public void revokeCurrentFamily(RefreshTokenHash presentedTokenHash, Instant revokedAt) {}

        @Override
        public void revokeAllFamiliesForUser(AuthenticatedPrincipal principal, Instant revokedAt) {
            revokedCount++;
        }

        int revokedCount() {
            return revokedCount;
        }
    }
}
