package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.application.command.LoginCommand;
import io.github.aayush10016.trail.identity.application.dto.LoginResult;
import io.github.aayush10016.trail.identity.application.dto.LoginStatus;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;
import io.github.aayush10016.trail.identity.domain.model.LoginAccount;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.LoginUserRepository;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginUserServiceTest {

    private static final UUID USER_ID = UUID.fromString("4c6526ac-9138-42c7-80fa-cce4d407ef6f");
    private static final Instant VERIFIED_AT = Instant.parse("2026-07-10T14:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-11T09:00:00Z");
    private static final PasswordHashRecord STORED_HASH =
            new PasswordHashRecord("stored-hash", "ARGON2ID", "m=old,t=1,p=1,salt=16,hash=32", 1);
    private static final PasswordHashRecord REHASHED_HASH =
            new PasswordHashRecord("rehash", "ARGON2ID", "m=new,t=2,p=1,salt=16,hash=32", 2);
    private static final IssuedAccessToken ISSUED_TOKEN =
            IssuedAccessToken.bearer("signed.jwt.token", Instant.parse("2026-07-10T14:10:00Z"));
    private static final IssuedRefreshToken ISSUED_REFRESH_TOKEN =
            IssuedRefreshToken.bearer("raw-refresh-token", Instant.parse("2026-08-09T14:00:00Z"));

    private final CapturingLoginUserRepository repository = new CapturingLoginUserRepository();
    private final FakePasswordPolicy passwordPolicy = new FakePasswordPolicy();
    private final CapturingAccessTokenIssuer accessTokenIssuer = new CapturingAccessTokenIssuer();
    private final CapturingRefreshSessionService refreshSessionService = new CapturingRefreshSessionService();
    private final LoginUserService service =
            new LoginUserService(
                    repository,
                    passwordPolicy,
                    accessTokenIssuer,
                    refreshSessionService,
                    IdentityRateLimiterTestDoubles.allowingRateLimiter(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void authenticatesVerifiedActiveAccountAndIssuesAccessToken() {
        repository.add(activeAccount("Developer@Example.com"));
        passwordPolicy.accept("correct-password", STORED_HASH);

        var result = service.login(new LoginCommand(" developer@example.COM ", "correct-password"));

        assertThat(result.status()).isEqualTo(LoginStatus.AUTHENTICATED);
        assertThat(result.accessToken()).isEqualTo(ISSUED_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);
        assertThat(accessTokenIssuer.issuedPrincipal()).isEqualTo(new AuthenticatedPrincipal(USER_ID));
        assertThat(refreshSessionService.principal()).isEqualTo(new AuthenticatedPrincipal(USER_ID));
        assertThat(repository.updatedPasswordHash()).isNull();
    }

    @Test
    void rejectsUnknownAccountWrongPasswordAndUnverifiedAccountWithSameGenericResult() {
        repository.add(unverifiedAccount("pending@example.com"));
        repository.add(activeAccount("wrong@example.com"));
        passwordPolicy.accept("correct-password", STORED_HASH);

        var unknownResult = service.login(new LoginCommand("missing@example.com", "correct-password"));
        var wrongPasswordResult = service.login(new LoginCommand("wrong@example.com", "wrong-password"));
        var unverifiedResult = service.login(new LoginCommand("pending@example.com", "correct-password"));

        assertThat(unknownResult).isEqualTo(LoginResult.rejected());
        assertThat(wrongPasswordResult).isEqualTo(LoginResult.rejected());
        assertThat(unverifiedResult).isEqualTo(LoginResult.rejected());
        assertThat(unknownResult).isEqualTo(wrongPasswordResult).isEqualTo(unverifiedResult);
        assertThat(accessTokenIssuer.issuedPrincipal()).isNull();
        assertThat(repository.updatedPasswordHash()).isNull();
    }

    @Test
    void rejectsLockedDeletedAndOversizedPasswordWithoutIssuingToken() {
        repository.add(account("locked@example.com", UserStatus.LOCKED, VERIFIED_AT));
        repository.add(account("deleted@example.com", UserStatus.DELETED, VERIFIED_AT));
        repository.add(account("active-unverified@example.com", UserStatus.ACTIVE, null));
        passwordPolicy.accept("correct-password", STORED_HASH);

        var lockedResult = service.login(new LoginCommand("locked@example.com", "correct-password"));
        var deletedResult = service.login(new LoginCommand("deleted@example.com", "correct-password"));
        var activeUnverifiedResult = service.login(new LoginCommand("active-unverified@example.com", "correct-password"));
        passwordPolicy.rejectOversizedInput();
        var oversizedResult = service.login(new LoginCommand("developer@example.com", "correct-password"));

        assertThat(lockedResult).isEqualTo(LoginResult.rejected());
        assertThat(deletedResult).isEqualTo(LoginResult.rejected());
        assertThat(activeUnverifiedResult).isEqualTo(LoginResult.rejected());
        assertThat(oversizedResult).isEqualTo(LoginResult.rejected());
        assertThat(accessTokenIssuer.issuedPrincipal()).isNull();
    }

    @Test
    void rehashesPasswordAfterSuccessfulLoginWhenPolicyRequiresIt() {
        repository.add(activeAccount("developer@example.com"));
        passwordPolicy.accept("correct-password", STORED_HASH);
        passwordPolicy.requireRehash();

        var result = service.login(new LoginCommand("developer@example.com", "correct-password"));

        assertThat(result.status()).isEqualTo(LoginStatus.AUTHENTICATED);
        assertThat(repository.updatedPasswordHash()).isEqualTo(REHASHED_HASH);
        assertThat(repository.updatedPasswordHashUserId()).isEqualTo(USER_ID);
    }

    private static LoginAccount activeAccount(String email) {
        return account(email, UserStatus.ACTIVE, VERIFIED_AT);
    }

    private static LoginAccount unverifiedAccount(String email) {
        return account(email, UserStatus.PENDING_VERIFICATION, null);
    }

    private static LoginAccount account(String email, UserStatus status, Instant verifiedAt) {
        return new LoginAccount(USER_ID, EmailAddress.from(email), STORED_HASH, status, verifiedAt);
    }

    private static final class CapturingLoginUserRepository implements LoginUserRepository {

        private final Map<String, LoginAccount> accountsByEmail = new HashMap<>();
        private UUID updatedPasswordHashUserId;
        private PasswordHashRecord updatedPasswordHash;

        @Override
        public Optional<LoginAccount> findByEmail(EmailAddress email) {
            return Optional.ofNullable(accountsByEmail.get(email.normalized()));
        }

        @Override
        public void updatePasswordHash(UUID userId, PasswordHashRecord passwordHash) {
            updatedPasswordHashUserId = userId;
            updatedPasswordHash = passwordHash;
        }

        void add(LoginAccount account) {
            accountsByEmail.put(account.email().normalized(), account);
        }

        UUID updatedPasswordHashUserId() {
            return updatedPasswordHashUserId;
        }

        PasswordHashRecord updatedPasswordHash() {
            return updatedPasswordHash;
        }
    }

    private static final class FakePasswordPolicy implements PasswordVerificationPolicy {

        private final Map<String, PasswordHashRecord> acceptedPasswords = new HashMap<>();
        private boolean oversizedInput;
        private boolean requiresRehash;

        @Override
        public boolean isWithinMaximumInputSize(CharSequence rawPassword) {
            return !oversizedInput;
        }

        @Override
        public PasswordHashRecord hash(CharSequence rawPassword) {
            return REHASHED_HASH;
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHashRecord passwordHash) {
            return passwordHash.equals(acceptedPasswords.get(rawPassword.toString()));
        }

        @Override
        public boolean requiresRehash(PasswordHashRecord passwordHash) {
            return requiresRehash;
        }

        void accept(String rawPassword, PasswordHashRecord passwordHash) {
            acceptedPasswords.put(rawPassword, passwordHash);
        }

        void rejectOversizedInput() {
            oversizedInput = true;
        }

        void requireRehash() {
            requiresRehash = true;
        }
    }

    private static final class CapturingAccessTokenIssuer implements AccessTokenIssuer {

        private AuthenticatedPrincipal issuedPrincipal;

        @Override
        public IssuedAccessToken issue(AuthenticatedPrincipal principal) {
            issuedPrincipal = principal;
            return ISSUED_TOKEN;
        }

        AuthenticatedPrincipal issuedPrincipal() {
            return issuedPrincipal;
        }
    }

    private static final class CapturingRefreshSessionService implements RefreshSessionCreator {

        private AuthenticatedPrincipal principal;

        @Override
        public IssuedRefreshToken createSession(AuthenticatedPrincipal principal) {
            this.principal = principal;
            return ISSUED_REFRESH_TOKEN;
        }

        AuthenticatedPrincipal principal() {
            return principal;
        }
    }
}
