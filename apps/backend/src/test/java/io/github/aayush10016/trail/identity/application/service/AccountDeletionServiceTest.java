package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.application.command.DeleteAccountCommand;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.port.AccountDeletionRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDeletionServiceTest {

    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("87000000-0000-4000-8000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final PasswordHashRecord PASSWORD_HASH =
            new PasswordHashRecord("encoded-password", "ARGON2ID", "parameters", 1);

    @Test
    void reauthenticatesRevokesRefreshSessionsBeforePurgingAccount() {
        var repository = new RecordingAccountDeletionRepository(Optional.of(PASSWORD_HASH), true);
        var service = new AccountDeletionService(
                repository,
                new MatchingPasswordPolicy("correct-password"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.delete(PRINCIPAL, new DeleteAccountCommand("correct-password"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.localCleanupRequired()).isTrue();
        assertThat(result.backupDisclosure()).contains("backups age out");
        assertThat(repository.calls()).containsExactly("find-password", "revoke-sessions", "purge-account");
        assertThat(repository.revokedAt()).containsExactly(NOW);
    }

    @Test
    void rejectsWrongPasswordWithoutRevokingOrPurging() {
        var repository = new RecordingAccountDeletionRepository(Optional.of(PASSWORD_HASH), true);
        var service = new AccountDeletionService(
                repository,
                new MatchingPasswordPolicy("correct-password"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.delete(PRINCIPAL, new DeleteAccountCommand("wrong-password")))
                .isInstanceOf(AccountDeletionReauthenticationException.class);

        assertThat(repository.calls()).containsExactly("find-password");
    }

    private static final class RecordingAccountDeletionRepository implements AccountDeletionRepository {

        private final Optional<PasswordHashRecord> passwordHash;
        private final boolean purgeResult;
        private final List<String> calls = new ArrayList<>();
        private final List<Instant> revokedAt = new ArrayList<>();

        private RecordingAccountDeletionRepository(Optional<PasswordHashRecord> passwordHash, boolean purgeResult) {
            this.passwordHash = passwordHash;
            this.purgeResult = purgeResult;
        }

        @Override
        public Optional<PasswordHashRecord> findActivePasswordHash(AuthenticatedPrincipal principal) {
            calls.add("find-password");
            return passwordHash;
        }

        @Override
        public void revokeRefreshSessions(AuthenticatedPrincipal principal, Instant revokedAt) {
            calls.add("revoke-sessions");
            this.revokedAt.add(revokedAt);
        }

        @Override
        public boolean purgeAccount(AuthenticatedPrincipal principal) {
            calls.add("purge-account");
            return purgeResult;
        }

        private List<String> calls() {
            return calls;
        }

        private List<Instant> revokedAt() {
            return revokedAt;
        }
    }

    private static final class MatchingPasswordPolicy implements PasswordVerificationPolicy {

        private final String acceptedPassword;

        private MatchingPasswordPolicy(String acceptedPassword) {
            this.acceptedPassword = acceptedPassword;
        }

        @Override
        public boolean isWithinMaximumInputSize(CharSequence rawPassword) {
            return rawPassword != null && rawPassword.length() <= 128;
        }

        @Override
        public PasswordHashRecord hash(CharSequence rawPassword) {
            return PASSWORD_HASH;
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHashRecord passwordHash) {
            return acceptedPassword.contentEquals(rawPassword);
        }

        @Override
        public boolean requiresRehash(PasswordHashRecord passwordHash) {
            return false;
        }
    }
}
