package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EmailActionTokenSecurityPolicyTest {

    @Test
    void secureGeneratorCreatesUrlSafeRandomTokensWithThirtyTwoBytesOfEntropy() {
        var generator = new SecureEmailVerificationTokenGenerator(new SecureRandom());

        var firstToken = generator.generateToken();
        var secondToken = generator.generateToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstToken).doesNotContain("+", "/", "=");
        assertThat(Base64.getUrlDecoder().decode(firstToken)).hasSize(SecureEmailVerificationTokenGenerator.TOKEN_BYTES);
    }

    @Test
    void passwordResetGeneratorCreatesUrlSafeRandomTokensWithThirtyTwoBytesOfEntropy() {
        var generator = new SecurePasswordResetTokenGenerator(new SecureRandom());

        var firstToken = generator.generateToken();
        var secondToken = generator.generateToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstToken).doesNotContain("+", "/", "=");
        assertThat(Base64.getUrlDecoder().decode(firstToken)).hasSize(SecurePasswordResetTokenGenerator.TOKEN_BYTES);
    }

    @Test
    void sha256HashingPolicyProducesStableThirtyTwoByteHashWithoutRawTokenText() {
        var hashingPolicy = new Sha256EmailActionTokenHashingPolicy();

        var firstHash = hashingPolicy.hash("raw-verification-token");
        var secondHash = hashingPolicy.hash("raw-verification-token");

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash.bytes()).hasSize(32);
        assertThat(new String(firstHash.bytes())).doesNotContain("raw-verification-token");
    }

    @Test
    void tokenHashDefensivelyCopiesBytes() {
        var hashingPolicy = new Sha256EmailActionTokenHashingPolicy();
        var tokenHash = hashingPolicy.hash("raw-verification-token");
        var exposedBytes = tokenHash.bytes();

        exposedBytes[0] = (byte) (exposedBytes[0] + 1);

        assertThat(tokenHash).isEqualTo(hashingPolicy.hash("raw-verification-token"));
    }
}
