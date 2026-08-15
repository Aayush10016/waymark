package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.aayush10016.trail.identity.application.command.ResetPasswordCommand;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetResult;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.PasswordHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:30:00Z");
    private static final UUID USER_ID = UUID.fromString("9d44aa88-b036-4e38-80a4-99b6e3a0928e");
    private static final AuthenticatedPrincipal PRINCIPAL = new AuthenticatedPrincipal(USER_ID);

    private final EmailActionTokenHashingPolicy tokenHashingPolicy = new Sha256EmailActionTokenHashingPolicy();
    private final CapturingPasswordHashingPolicy passwordHashingPolicy = new CapturingPasswordHashingPolicy();
    private final CapturingPasswordResetTokenRepository tokenRepository = new CapturingPasswordResetTokenRepository();
    private final CapturingRefreshSessionRepository refreshSessionRepository = new CapturingRefreshSessionRepository();
    private final PasswordResetService service =
            new PasswordResetService(
                    tokenRepository,
                    refreshSessionRepository,
                    tokenHashingPolicy,
                    passwordHashingPolicy,
                    IdentityRateLimiterTestDoubles.allowingRateLimiter(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void consumesActiveTokenUpdatesPasswordAndRevokesAllRefreshFamilies() {
        tokenRepository.activate(tokenHashingPolicy.hash("raw-reset-token"), PRINCIPAL);

        var result = service.resetPassword(new ResetPasswordCommand("raw-reset-token", "new secure password"));

        assertThat(result).isEqualTo(PasswordResetResult.accepted());
        assertThat(tokenRepository.consumeResults()).containsExactly(PasswordResetTokenRepository.ConsumeResult.consumed(PRINCIPAL));
        assertThat(tokenRepository.passwordHashes()).containsExactly(passwordHashingPolicy.hash("new secure password"));
        assertThat(tokenRepository.passwordHashes().get(0).encodedHash()).doesNotContain("new secure password");
        assertThat(refreshSessionRepository.revokedPrincipals()).containsExactly(PRINCIPAL);
        assertThat(refreshSessionRepository.revokedAt()).containsExactly(NOW);
    }

    @Test
    void resetTokenCanOnlyBeConsumedOnceAndSecondAttemptDoesNotRevokeAgain() {
        tokenRepository.activate(tokenHashingPolicy.hash("raw-reset-token"), PRINCIPAL);

        var first = service.resetPassword(new ResetPasswordCommand("raw-reset-token", "new secure password"));
        var second = service.resetPassword(new ResetPasswordCommand("raw-reset-token", "another secure password"));

        assertThat(first).isEqualTo(PasswordResetResult.accepted());
        assertThat(second).isEqualTo(PasswordResetResult.accepted());
        assertThat(tokenRepository.consumeResults())
                .containsExactly(
                        PasswordResetTokenRepository.ConsumeResult.consumed(PRINCIPAL),
                        PasswordResetTokenRepository.ConsumeResult.notActive());
        assertThat(refreshSessionRepository.revokedPrincipals()).containsExactly(PRINCIPAL);
    }

    @Test
    void expiredOrUnknownTokenReturnsGenericResultWithoutRevokingSessions() {
        tokenRepository.activateExpired(tokenHashingPolicy.hash("expired-reset-token"), PRINCIPAL);

        var expired = service.resetPassword(new ResetPasswordCommand("expired-reset-token", "new secure password"));
        var unknown = service.resetPassword(new ResetPasswordCommand("unknown-reset-token", "new secure password"));

        assertThat(expired).isEqualTo(PasswordResetResult.accepted());
        assertThat(unknown).isEqualTo(PasswordResetResult.accepted());
        assertThat(tokenRepository.consumeResults())
                .containsExactly(
                        PasswordResetTokenRepository.ConsumeResult.notActive(),
                        PasswordResetTokenRepository.ConsumeResult.notActive());
        assertThat(refreshSessionRepository.revokedPrincipals()).isEmpty();
    }

    @Test
    void oversizedPasswordIsRejectedBeforeTokenConsumption() {
        passwordHashingPolicy.rejectNextPassword();

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                service.resetPassword(
                                        new ResetPasswordCommand("raw-reset-token", "oversized password")));

        assertThat(tokenRepository.consumeResults()).isEmpty();
        assertThat(refreshSessionRepository.revokedPrincipals()).isEmpty();
    }

    private static final class CapturingPasswordHashingPolicy implements PasswordHashingPolicy {

        private boolean rejectNextPassword;

        @Override
        public boolean isWithinMaximumInputSize(CharSequence rawPassword) {
            return rawPassword != null && !rejectNextPassword;
        }

        @Override
        public PasswordHashRecord hash(CharSequence rawPassword) {
            return new PasswordHashRecord("hashed-" + rawPassword.hashCode(), "TEST", "test-parameters", 1);
        }

        void rejectNextPassword() {
            rejectNextPassword = true;
        }
    }

    private static final class CapturingPasswordResetTokenRepository implements PasswordResetTokenRepository {

        private final List<EmailActionTokenHash> activeTokenHashes = new ArrayList<>();
        private final List<EmailActionTokenHash> expiredTokenHashes = new ArrayList<>();
        private final List<ConsumeResult> consumeResults = new ArrayList<>();
        private final List<PasswordHashRecord> passwordHashes = new ArrayList<>();
        private AuthenticatedPrincipal principal;

        @Override
        public StoreResult storeForActiveEmail(
                io.github.aayush10016.trail.identity.domain.model.EmailAddress email,
                io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken token) {
            return StoreResult.noActiveUser();
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, PasswordHashRecord newPasswordHash, Instant consumedAt) {
            if (expiredTokenHashes.contains(tokenHash) || !activeTokenHashes.remove(tokenHash)) {
                consumeResults.add(ConsumeResult.notActive());
                return ConsumeResult.notActive();
            }

            passwordHashes.add(newPasswordHash);
            var result = ConsumeResult.consumed(principal);
            consumeResults.add(result);
            return result;
        }

        void activate(EmailActionTokenHash tokenHash, AuthenticatedPrincipal principal) {
            this.principal = principal;
            activeTokenHashes.add(tokenHash);
        }

        void activateExpired(EmailActionTokenHash tokenHash, AuthenticatedPrincipal principal) {
            this.principal = principal;
            expiredTokenHashes.add(tokenHash);
        }

        List<ConsumeResult> consumeResults() {
            return List.copyOf(consumeResults);
        }

        List<PasswordHashRecord> passwordHashes() {
            return List.copyOf(passwordHashes);
        }
    }

    private static final class CapturingRefreshSessionRepository implements RefreshSessionRepository {

        private final List<AuthenticatedPrincipal> revokedPrincipals = new ArrayList<>();
        private final List<Instant> revokedAt = new ArrayList<>();

        @Override
        public void create(StoredRefreshSession session) {}

        @Override
        public RotationResult rotate(RefreshTokenHash presentedTokenHash, RefreshSessionReplacement replacement, Instant rotatedAt) {
            return RotationResult.notActive();
        }

        @Override
        public void revokeCurrentFamily(RefreshTokenHash presentedTokenHash, Instant revokedAt) {}

        @Override
        public void revokeAllFamiliesForUser(AuthenticatedPrincipal principal, Instant revokedAt) {
            if (!revokedPrincipals.contains(principal)) {
                revokedPrincipals.add(principal);
                this.revokedAt.add(revokedAt);
            }
        }

        List<AuthenticatedPrincipal> revokedPrincipals() {
            return List.copyOf(revokedPrincipals);
        }

        List<Instant> revokedAt() {
            return List.copyOf(revokedAt);
        }
    }
}
