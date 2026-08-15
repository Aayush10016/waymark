package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class Argon2idPasswordPolicyTest {

    private final Argon2idPasswordPolicy policy = new Argon2idPasswordPolicy();

    @Test
    void exposesCurrentVersionedMetadata() {
        assertThat(policy.currentMetadata())
                .isEqualTo(new PasswordHashMetadata("ARGON2ID", 1, "m=19456,t=2,p=1,salt=16,hash=32"));
    }

    @Test
    void enforcesMaximumUtf8InputSize() {
        assertThat(policy.isWithinMaximumInputSize("a".repeat(Argon2idPasswordPolicy.MAXIMUM_PASSWORD_BYTES)))
                .isTrue();
        assertThat(policy.isWithinMaximumInputSize("a".repeat(Argon2idPasswordPolicy.MAXIMUM_PASSWORD_BYTES + 1)))
                .isFalse();
        assertThat(policy.isWithinMaximumInputSize("\u20ac".repeat(342))).isFalse();
        assertThat(policy.isWithinMaximumInputSize(null)).isFalse();
    }

    @Test
    void encodesAndMatchesPasswordWithCurrentPolicy() {
        var encodedPassword = policy.passwordEncoder().encode("correct horse battery staple");

        assertThat(encodedPassword).startsWith("$argon2id$");
        assertThat(policy.passwordEncoder().matches("correct horse battery staple", encodedPassword)).isTrue();
        assertThat(policy.passwordEncoder().matches("incorrect", encodedPassword)).isFalse();
        assertThat(policy.requiresRehash(encodedPassword, policy.currentMetadata())).isFalse();
    }

    @Test
    void requestsRehashWhenStoredMetadataIsOutdated() {
        assertThat(policy.requiresRehash(new PasswordHashMetadata("ARGON2ID", 1, "m=12288,t=3,p=1,salt=16,hash=32")))
                .isTrue();
        assertThat(policy.requiresRehash(new PasswordHashMetadata("ARGON2ID", 2, "m=19456,t=2,p=1,salt=16,hash=32")))
                .isTrue();
        assertThat(policy.requiresRehash(new PasswordHashMetadata("BCRYPT", 1, "cost=12"))).isTrue();
    }

    @Test
    void requestsRehashWhenEncodedPasswordUsesWeakerParameters() {
        var weakerEncoder = new Argon2PasswordEncoder(16, 32, 1, 12_288, 2);
        var weakerHash = weakerEncoder.encode("correct horse battery staple");

        assertThat(policy.requiresRehash(weakerHash, policy.currentMetadata())).isTrue();
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHashMetadata("", 1, "m=1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHashMetadata("ARGON2ID", 0, "m=1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHashMetadata("ARGON2ID", 1, " "));
    }
}
