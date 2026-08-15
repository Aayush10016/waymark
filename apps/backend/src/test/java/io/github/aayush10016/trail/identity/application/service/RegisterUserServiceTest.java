package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.aayush10016.trail.identity.application.command.RegisterUserCommand;
import io.github.aayush10016.trail.identity.application.dto.RegistrationResult;
import io.github.aayush10016.trail.identity.domain.model.RegisteredUser;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.UserRegistrationRepository;
import io.github.aayush10016.trail.identity.security.Argon2idPasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T13:30:00Z");
    private static final UUID USER_ID = UUID.fromString("7a03c9ad-3049-4cc4-b940-880c52f2bb91");

    private final Argon2idPasswordPolicy passwordPolicy = new Argon2idPasswordPolicy();
    private final CapturingRegistrationRepository repository = new CapturingRegistrationRepository();
    private final RegisterUserService service =
            new RegisterUserService(
                    repository,
                    passwordPolicy,
                    IdentityRateLimiterTestDoubles.allowingRateLimiter(),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> USER_ID);

    @Test
    void normalizesEmailAndCreatesPendingUser() {
        var result = service.register(new RegisterUserCommand(" Developer@Example.COM ", "correct horse battery staple"));

        assertThat(result).isEqualTo(RegistrationResult.accepted());
        assertThat(repository.persistedUsers()).hasSize(1);

        var storedUser = repository.persistedUsers().get(0);
        assertThat(storedUser.id()).isEqualTo(USER_ID);
        assertThat(storedUser.email().display()).isEqualTo("Developer@Example.COM");
        assertThat(storedUser.email().normalized()).isEqualTo("developer@example.com");
        assertThat(storedUser.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(storedUser.createdAt()).isEqualTo(NOW);
    }

    @Test
    void storesArgon2idHashAndMetadataWithoutRawPassword() {
        service.register(new RegisterUserCommand("developer@example.com", "correct horse battery staple"));

        var passwordHash = repository.persistedUsers().get(0).passwordHash();

        assertThat(passwordHash.encodedHash()).startsWith("$argon2id$");
        assertThat(passwordHash.encodedHash()).doesNotContain("correct horse battery staple");
        assertThat(passwordHash.algorithm()).isEqualTo("ARGON2ID");
        assertThat(passwordHash.version()).isEqualTo(1);
        assertThat(passwordHash.parameters()).isEqualTo("m=19456,t=2,p=1,salt=16,hash=32");
        assertThat(passwordPolicy.passwordEncoder().matches("correct horse battery staple", passwordHash.encodedHash()))
                .isTrue();
    }

    @Test
    void returnsSameGenericResultWhenEmailAlreadyExists() {
        repository.markRegistered("developer@example.com");

        var result = service.register(new RegisterUserCommand("Developer@Example.COM", "correct horse battery staple"));

        assertThat(result).isEqualTo(RegistrationResult.accepted());
        assertThat(repository.attemptedUsers()).hasSize(1);
        assertThat(repository.persistedUsers()).isEmpty();
        assertThat(repository.storeResults()).containsExactly(UserRegistrationRepository.StoreResult.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void rejectsPasswordAboveConfiguredByteLimitBeforePersistence() {
        var oversizedPassword = "a".repeat(Argon2idPasswordPolicy.MAXIMUM_PASSWORD_BYTES + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register(new RegisterUserCommand("developer@example.com", oversizedPassword)));

        assertThat(repository.attemptedUsers()).isEmpty();
        assertThat(repository.persistedUsers()).isEmpty();
    }

    @Test
    void rejectsInvalidEmailBeforePersistence() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register(new RegisterUserCommand("invalid-email", "correct horse battery staple")));

        assertThat(repository.attemptedUsers()).isEmpty();
        assertThat(repository.persistedUsers()).isEmpty();
    }

    private static final class CapturingRegistrationRepository implements UserRegistrationRepository {

        private final Set<String> registeredEmails = new HashSet<>();
        private final List<RegisteredUser> attemptedUsers = new ArrayList<>();
        private final List<RegisteredUser> persistedUsers = new ArrayList<>();
        private final List<StoreResult> storeResults = new ArrayList<>();

        @Override
        public StoreResult store(RegisteredUser user) {
            attemptedUsers.add(user);
            if (!registeredEmails.add(user.email().normalized())) {
                storeResults.add(StoreResult.EMAIL_ALREADY_REGISTERED);
                return StoreResult.EMAIL_ALREADY_REGISTERED;
            }
            persistedUsers.add(user);
            storeResults.add(StoreResult.CREATED);
            return StoreResult.CREATED;
        }

        void markRegistered(String normalizedEmail) {
            registeredEmails.add(normalizedEmail);
        }

        List<RegisteredUser> attemptedUsers() {
            return List.copyOf(attemptedUsers);
        }

        List<RegisteredUser> persistedUsers() {
            return List.copyOf(persistedUsers);
        }

        List<StoreResult> storeResults() {
            return List.copyOf(storeResults);
        }
    }
}
