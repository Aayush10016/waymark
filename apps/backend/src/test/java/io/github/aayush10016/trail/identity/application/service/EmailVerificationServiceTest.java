package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.application.command.ConsumeEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.dto.EmailVerificationResult;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T13:30:00Z");
    private static final UUID TOKEN_ID = UUID.fromString("6c63e966-9458-4df2-8ff7-6ed4f1809ea7");
    private static final Duration TOKEN_TIME_TO_LIVE = Duration.ofHours(2);

    private final EmailActionTokenHashingPolicy hashingPolicy = new Sha256EmailActionTokenHashingPolicy();
    private final FixedTokenGenerator tokenGenerator = new FixedTokenGenerator("raw-verification-token");
    private final CapturingVerificationTokenRepository repository = new CapturingVerificationTokenRepository();
    private final CapturingEmailDeliveryPort emailDeliveryPort = new CapturingEmailDeliveryPort();
    private final EmailVerificationService service =
            new EmailVerificationService(
                    repository,
                    tokenGenerator,
                    hashingPolicy,
                    emailDeliveryPort,
                    new IdentityEmailTemplateFactory(
                            URI.create("https://app.trail.test/verify"),
                            URI.create("https://app.trail.test/reset")),
                    IdentityRateLimiterTestDoubles.allowingRateLimiter(),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> TOKEN_ID,
                    TOKEN_TIME_TO_LIVE);

    @Test
    void storesHashedExpiringTokenForPendingEmailAndReturnsGenericResult() {
        repository.markPending("developer@example.com");

        var result = service.requestVerification(new RequestEmailVerificationCommand(" Developer@Example.COM "));

        assertThat(result).isEqualTo(EmailVerificationResult.accepted());
        assertThat(repository.storeResults()).containsExactly(EmailVerificationTokenRepository.StoreResult.STORED);
        assertThat(repository.storedTokens()).hasSize(1);

        var storedToken = repository.storedTokens().get(0);
        assertThat(storedToken.id()).isEqualTo(TOKEN_ID);
        assertThat(storedToken.createdAt()).isEqualTo(NOW);
        assertThat(storedToken.expiresAt()).isEqualTo(NOW.plus(TOKEN_TIME_TO_LIVE));
        assertThat(storedToken.tokenHash()).isEqualTo(hashingPolicy.hash("raw-verification-token"));
        assertThat(new String(storedToken.tokenHash().bytes())).doesNotContain("raw-verification-token");
        assertThat(emailDeliveryPort.messages()).hasSize(1);
        assertThat(emailDeliveryPort.messages().get(0).recipient()).isEqualTo(EmailAddress.from("Developer@Example.COM"));
        assertThat(emailDeliveryPort.messages().get(0).textBody()).contains("raw-verification-token");
    }

    @Test
    void resendReturnsSameGenericResultWhenEmailHasNoPendingAccount() {
        var result = service.requestVerification(new RequestEmailVerificationCommand("missing@example.com"));

        assertThat(result).isEqualTo(EmailVerificationResult.accepted());
        assertThat(repository.storeResults()).containsExactly(EmailVerificationTokenRepository.StoreResult.NO_PENDING_USER);
        assertThat(repository.storedTokens()).isEmpty();
        assertThat(emailDeliveryPort.messages()).isEmpty();
    }

    @Test
    void consumesActiveTokenOnlyOnceAndReturnsGenericResult() {
        var tokenHash = hashingPolicy.hash("raw-verification-token");
        repository.addActiveToken(tokenHash, NOW.plus(TOKEN_TIME_TO_LIVE));

        var firstResult = service.consumeVerification(new ConsumeEmailVerificationCommand("raw-verification-token"));
        var secondResult = service.consumeVerification(new ConsumeEmailVerificationCommand("raw-verification-token"));

        assertThat(firstResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(secondResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(repository.consumeResults())
                .containsExactly(
                        EmailVerificationTokenRepository.ConsumeResult.CONSUMED,
                        EmailVerificationTokenRepository.ConsumeResult.NOT_ACTIVE);
        assertThat(repository.consumedTokenHashes()).containsExactly(tokenHash);
    }

    @Test
    void consumeReturnsGenericResultForExpiredOrUnknownToken() {
        repository.addActiveToken(hashingPolicy.hash("expired-token"), NOW.minusSeconds(1));

        var expiredResult = service.consumeVerification(new ConsumeEmailVerificationCommand("expired-token"));
        var unknownResult = service.consumeVerification(new ConsumeEmailVerificationCommand("unknown-token"));

        assertThat(expiredResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(unknownResult).isEqualTo(EmailVerificationResult.accepted());
        assertThat(repository.consumeResults())
                .containsExactly(
                        EmailVerificationTokenRepository.ConsumeResult.NOT_ACTIVE,
                        EmailVerificationTokenRepository.ConsumeResult.NOT_ACTIVE);
        assertThat(repository.consumedTokenHashes()).isEmpty();
    }

    private static final class FixedTokenGenerator implements EmailVerificationTokenGenerator {

        private final String token;

        private FixedTokenGenerator(String token) {
            this.token = token;
        }

        @Override
        public String generateToken() {
            return token;
        }
    }

    private static final class CapturingVerificationTokenRepository implements EmailVerificationTokenRepository {

        private final Set<String> pendingEmails = new HashSet<>();
        private final List<StoredEmailVerificationToken> storedTokens = new ArrayList<>();
        private final List<StoreResult> storeResults = new ArrayList<>();
        private final Map<EmailActionTokenHash, StoredTokenState> activeTokens = new HashMap<>();
        private final List<ConsumeResult> consumeResults = new ArrayList<>();
        private final List<EmailActionTokenHash> consumedTokenHashes = new ArrayList<>();

        @Override
        public StoreResult storeForPendingEmail(EmailAddress email, StoredEmailVerificationToken token) {
            if (!pendingEmails.contains(email.normalized())) {
                storeResults.add(StoreResult.NO_PENDING_USER);
                return StoreResult.NO_PENDING_USER;
            }

            storedTokens.add(token);
            activeTokens.put(token.tokenHash(), new StoredTokenState(token.expiresAt(), false));
            storeResults.add(StoreResult.STORED);
            return StoreResult.STORED;
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, Instant consumedAt) {
            var storedToken = activeTokens.get(tokenHash);
            if (storedToken == null || storedToken.consumed() || !storedToken.expiresAt().isAfter(consumedAt)) {
                consumeResults.add(ConsumeResult.NOT_ACTIVE);
                return ConsumeResult.NOT_ACTIVE;
            }

            activeTokens.put(tokenHash, new StoredTokenState(storedToken.expiresAt(), true));
            consumedTokenHashes.add(tokenHash);
            consumeResults.add(ConsumeResult.CONSUMED);
            return ConsumeResult.CONSUMED;
        }

        void markPending(String normalizedEmail) {
            pendingEmails.add(normalizedEmail);
        }

        void addActiveToken(EmailActionTokenHash tokenHash, Instant expiresAt) {
            activeTokens.put(tokenHash, new StoredTokenState(expiresAt, false));
        }

        List<StoredEmailVerificationToken> storedTokens() {
            return List.copyOf(storedTokens);
        }

        List<StoreResult> storeResults() {
            return List.copyOf(storeResults);
        }

        List<ConsumeResult> consumeResults() {
            return List.copyOf(consumeResults);
        }

        List<EmailActionTokenHash> consumedTokenHashes() {
            return List.copyOf(consumedTokenHashes);
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

    private record StoredTokenState(Instant expiresAt, boolean consumed) {}
}
