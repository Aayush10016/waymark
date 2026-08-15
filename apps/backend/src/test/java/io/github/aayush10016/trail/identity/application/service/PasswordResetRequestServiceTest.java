package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetRequestResult;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T07:30:00Z");
    private static final UUID TOKEN_ID = UUID.fromString("ad536316-511b-4764-b6f3-ce9959355d9b");
    private static final Duration TOKEN_TIME_TO_LIVE = Duration.ofMinutes(30);
    private static final EmailAddress CANONICAL_RECIPIENT = EmailAddress.from("developer@example.com");

    private final EmailActionTokenHashingPolicy hashingPolicy = new Sha256EmailActionTokenHashingPolicy();
    private final FixedPasswordResetTokenGenerator tokenGenerator = new FixedPasswordResetTokenGenerator("raw-reset-token");
    private final CapturingPasswordResetTokenRepository repository = new CapturingPasswordResetTokenRepository();
    private final CapturingEmailDeliveryPort emailDeliveryPort = new CapturingEmailDeliveryPort();
    private final IdentityRateLimiterTestDoubles.CapturingIdentityRateLimiter rateLimiter =
            IdentityRateLimiterTestDoubles.allowingRateLimiter();
    private final PasswordResetRequestService service =
            new PasswordResetRequestService(
                    repository,
                    tokenGenerator,
                    hashingPolicy,
                    emailDeliveryPort,
                    new IdentityEmailTemplateFactory(
                            URI.create("https://app.trail.test/verify-email"),
                            URI.create("https://app.trail.test/reset-password")),
                    rateLimiter,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> TOKEN_ID,
                    TOKEN_TIME_TO_LIVE);

    @Test
    void storesHashedExpiringResetTokenForActiveEmailAndReturnsGenericResult() {
        repository.markActive(CANONICAL_RECIPIENT);

        var result = service.requestReset(new RequestPasswordResetCommand(" Developer@Example.COM "));

        assertThat(result).isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(repository.storeResults()).containsExactly(PasswordResetTokenRepository.StoreResult.stored(CANONICAL_RECIPIENT));
        assertThat(repository.storedTokens()).hasSize(1);

        var storedToken = repository.storedTokens().get(0);
        assertThat(storedToken.id()).isEqualTo(TOKEN_ID);
        assertThat(storedToken.createdAt()).isEqualTo(NOW);
        assertThat(storedToken.expiresAt()).isEqualTo(NOW.plus(TOKEN_TIME_TO_LIVE));
        assertThat(storedToken.tokenHash()).isEqualTo(hashingPolicy.hash("raw-reset-token"));
        assertThat(new String(storedToken.tokenHash().bytes())).doesNotContain("raw-reset-token");
        assertThat(emailDeliveryPort.messages()).hasSize(1);
        assertThat(emailDeliveryPort.messages().get(0).recipient()).isEqualTo(CANONICAL_RECIPIENT);
        assertThat(emailDeliveryPort.messages().get(0).textBody()).contains("raw-reset-token");
    }

    @Test
    void returnsGenericResultWithoutTokenOrEmailWhenNoActiveAccountExists() {
        var result = service.requestReset(new RequestPasswordResetCommand("missing@example.com"));

        assertThat(result).isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(repository.storeResults()).containsExactly(PasswordResetTokenRepository.StoreResult.noActiveUser());
        assertThat(repository.storedTokens()).isEmpty();
        assertThat(emailDeliveryPort.messages()).isEmpty();
    }

    @Test
    void rateLimitDenialReturnsGenericResultWithoutGeneratingOrStoringToken() {
        repository.markActive(CANONICAL_RECIPIENT);
        var serviceWithDeniedRateLimit =
                new PasswordResetRequestService(
                        repository,
                        tokenGenerator,
                        hashingPolicy,
                        emailDeliveryPort,
                        new IdentityEmailTemplateFactory(
                                URI.create("https://app.trail.test/verify-email"),
                                URI.create("https://app.trail.test/reset-password")),
                        IdentityRateLimiterTestDoubles.denyingRateLimiter(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> TOKEN_ID,
                        TOKEN_TIME_TO_LIVE);

        var result = serviceWithDeniedRateLimit.requestReset(new RequestPasswordResetCommand("developer@example.com"));

        assertThat(result).isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(tokenGenerator.generatedCount()).isZero();
        assertThat(repository.storeResults()).isEmpty();
        assertThat(emailDeliveryPort.messages()).isEmpty();
    }

    @Test
    void emailDeliveryFailureStillReturnsGenericResultWithoutLeakingAccountExistence() {
        repository.markActive(CANONICAL_RECIPIENT);
        emailDeliveryPort.failPermanently();

        var result = service.requestReset(new RequestPasswordResetCommand("developer@example.com"));

        assertThat(result).isEqualTo(PasswordResetRequestResult.accepted());
        assertThat(repository.storedTokens()).hasSize(1);
        assertThat(emailDeliveryPort.messages()).hasSize(1);
    }

    @Test
    void storedPasswordResetTokenRejectsNonExpiringMetadata() {
        var tokenHash = hashingPolicy.hash("raw-reset-token");

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new StoredPasswordResetToken(
                                        TOKEN_ID,
                                        tokenHash,
                                        NOW,
                                        NOW));
    }

    private static final class FixedPasswordResetTokenGenerator implements PasswordResetTokenGenerator {

        private final String token;
        private int generatedCount;

        private FixedPasswordResetTokenGenerator(String token) {
            this.token = token;
        }

        @Override
        public String generateToken() {
            generatedCount++;
            return token;
        }

        int generatedCount() {
            return generatedCount;
        }
    }

    private static final class CapturingPasswordResetTokenRepository implements PasswordResetTokenRepository {

        private final Set<String> activeEmails = new HashSet<>();
        private final List<StoredPasswordResetToken> storedTokens = new ArrayList<>();
        private final List<StoreResult> storeResults = new ArrayList<>();

        @Override
        public StoreResult storeForActiveEmail(EmailAddress email, StoredPasswordResetToken token) {
            if (!activeEmails.contains(email.normalized())) {
                storeResults.add(StoreResult.noActiveUser());
                return StoreResult.noActiveUser();
            }

            storedTokens.add(token);
            storeResults.add(StoreResult.stored(CANONICAL_RECIPIENT));
            return StoreResult.stored(CANONICAL_RECIPIENT);
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, PasswordHashRecord newPasswordHash, Instant consumedAt) {
            return ConsumeResult.notActive();
        }

        void markActive(EmailAddress email) {
            activeEmails.add(email.normalized());
        }

        List<StoredPasswordResetToken> storedTokens() {
            return List.copyOf(storedTokens);
        }

        List<StoreResult> storeResults() {
            return List.copyOf(storeResults);
        }
    }

    private static final class CapturingEmailDeliveryPort implements EmailDeliveryPort {

        private final List<EmailDeliveryMessage> messages = new ArrayList<>();
        private boolean failPermanently;

        @Override
        public void send(EmailDeliveryMessage message) {
            messages.add(message);
            if (failPermanently) {
                throw EmailDeliveryException.permanent("password reset delivery failed", null);
            }
        }

        void failPermanently() {
            failPermanently = true;
        }

        List<EmailDeliveryMessage> messages() {
            return List.copyOf(messages);
        }
    }

}
