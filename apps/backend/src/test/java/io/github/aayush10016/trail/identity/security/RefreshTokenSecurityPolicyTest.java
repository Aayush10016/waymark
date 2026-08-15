package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RefreshTokenSecurityPolicyTest {

    @Test
    void generatesRandomUrlSafeOpaqueRefreshTokens() {
        var generator = new SecureRefreshTokenGenerator(new SecureRandom());

        var firstToken = generator.generateToken();
        var secondToken = generator.generateToken();

        assertThat(firstToken).isNotBlank().isNotEqualTo(secondToken);
        assertThat(firstToken).doesNotContain("+", "/", "=");
        assertThat(Base64.getUrlDecoder().decode(firstToken)).hasSize(SecureRefreshTokenGenerator.TOKEN_BYTES);
    }

    @Test
    void hashesRefreshTokenWithoutRetainingRawTokenText() {
        var hashingPolicy = new Sha256RefreshTokenHashingPolicy();

        var hash = hashingPolicy.hash("raw-refresh-token");

        assertThat(hash.bytes()).hasSize(32);
        assertThat(new String(hash.bytes())).doesNotContain("raw-refresh-token");
        assertThat(hash).isEqualTo(hashingPolicy.hash("raw-refresh-token"));
        assertThat(hash).isNotEqualTo(hashingPolicy.hash("other-refresh-token"));
    }
}
