package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtAccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-07-10T14:30:00Z");
    private static final UUID USER_ID = UUID.fromString("d3455b5e-183d-4506-b8ef-48ff4e18f0af");
    private static final UUID TOKEN_ID = UUID.fromString("af15bb42-2ae7-47c1-a0d8-030f6bce3220");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void issuesSignedRs256JwtWithRequiredClaimsAudienceAndExpiry() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();
        var issuer =
                new JwtAccessTokenIssuer(
                        keyPair.getPrivate(),
                        "test-key-1",
                        "https://api.trail.test",
                        "trail-extension",
                        Duration.ofMinutes(10),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> TOKEN_ID);

        var result = issuer.issue(new AuthenticatedPrincipal(USER_ID));

        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));

        var segments = result.token().split("\\.");
        assertThat(segments).hasSize(3);

        var header = decodeJson(segments[0]);
        assertThat(header)
                .containsEntry("alg", JwtAccessTokenIssuer.ALGORITHM)
                .containsEntry("typ", "JWT")
                .containsEntry("kid", "test-key-1");

        var claims = decodeJson(segments[1]);
        assertThat(claims)
                .containsEntry("iss", "https://api.trail.test")
                .containsEntry("sub", USER_ID.toString())
                .containsEntry("aud", "trail-extension")
                .containsEntry("jti", TOKEN_ID.toString());
        assertThat(((Number) claims.get("iat")).longValue()).isEqualTo(NOW.getEpochSecond());
        assertThat(((Number) claims.get("nbf")).longValue()).isEqualTo(NOW.getEpochSecond());
        assertThat(((Number) claims.get("exp")).longValue())
                .isEqualTo(NOW.plus(Duration.ofMinutes(10)).getEpochSecond());

        assertThat(signatureIsValid(segments, keyPair.getPublic().getEncoded())).isTrue();
    }

    private static Map<String, Object> decodeJson(String segment) throws Exception {
        var json = new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
        return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    }

    private static boolean signatureIsValid(String[] segments, byte[] encodedPublicKey) throws Exception {
        var keyFactory = java.security.KeyFactory.getInstance("RSA");
        var publicKey = keyFactory.generatePublic(new java.security.spec.X509EncodedKeySpec(encodedPublicKey));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        return signature.verify(Base64.getUrlDecoder().decode(segments[2]));
    }
}
