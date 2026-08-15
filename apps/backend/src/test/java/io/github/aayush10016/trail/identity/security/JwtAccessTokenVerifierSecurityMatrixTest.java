package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtAccessTokenVerifierSecurityMatrixTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("5b31e5be-dcbd-4421-8a66-8c5998ffbe90");
    private static final String KEY_ID = "trail-test-key";
    private static final String ISSUER = "trail-local";
    private static final String AUDIENCE = "trail-extension";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private KeyPair keyPair;
    private JwtAccessTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
        verifier =
                new JwtAccessTokenVerifier(
                        keyPair.getPublic(),
                        KEY_ID,
                        ISSUER,
                        AUDIENCE,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsOnlyRs256TokensWithExpectedIssuerAudienceAndSubject() throws Exception {
        var token = signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(NOW, NOW.plusSeconds(300)));

        assertThat(verifier.verify(token)).contains(new AuthenticatedPrincipal(USER_ID));
    }

    @Test
    void rejectsAlgorithmConfusionAndUnsignedTokens() throws Exception {
        var wrongAlgorithm = signedToken(header("HS256"), claims(NOW, NOW.plusSeconds(300)));
        var unsigned = encoded(header("none")) + "." + encoded(claims(NOW, NOW.plusSeconds(300))) + ".";

        assertThat(verifier.verify(wrongAlgorithm)).isEmpty();
        assertThat(verifier.verify(unsigned)).isEmpty();
    }

    @Test
    void rejectsWrongAudienceIssuerKeyIdAndMalformedSubject() throws Exception {
        assertThat(verifier.verify(signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(
                                NOW,
                                NOW.plusSeconds(300),
                                ISSUER,
                                "wrong-audience",
                                USER_ID.toString()))))
                .isEmpty();
        assertThat(verifier.verify(signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(
                                NOW,
                                NOW.plusSeconds(300),
                                "wrong-issuer",
                                AUDIENCE,
                                USER_ID.toString()))))
                .isEmpty();
        assertThat(verifier.verify(signedToken(headerWithKeyId("other-key"), claims(NOW, NOW.plusSeconds(300)))))
                .isEmpty();
        assertThat(verifier.verify(signedToken(
                        header(JwtAccessTokenIssuer.ALGORITHM),
                        claims(NOW, NOW.plusSeconds(300), ISSUER, AUDIENCE, "not-a-uuid"))))
                .isEmpty();
    }

    @Test
    void rejectsExpiredNotYetValidAndBoundarySkewTokens() throws Exception {
        assertThat(verifier.verify(signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(
                                NOW.minusSeconds(600),
                                NOW.minusSeconds(1)))))
                .isEmpty();
        assertThat(verifier.verify(signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(
                                NOW.plusSeconds(1),
                                NOW.plusSeconds(300)))))
                .isEmpty();
        assertThat(verifier.verify(signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(
                                NOW.minusSeconds(300),
                                NOW))))
                .isEmpty();
    }

    @Test
    void rejectsTamperedSignatureMalformedStructureAndBlankInput() throws Exception {
        var token = signedToken(header(JwtAccessTokenIssuer.ALGORITHM), claims(NOW, NOW.plusSeconds(300)));
        var tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(verifier.verify(tampered)).isEmpty();
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
        assertThat(verifier.verify(" ")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }

    private static Map<String, Object> header(String algorithm) {
        return headerWithKeyId(KEY_ID, algorithm);
    }

    private static Map<String, Object> headerWithKeyId(String keyId) {
        return headerWithKeyId(keyId, JwtAccessTokenIssuer.ALGORITHM);
    }

    private static Map<String, Object> headerWithKeyId(String keyId, String algorithm) {
        var header = new LinkedHashMap<String, Object>();
        header.put("alg", algorithm);
        header.put("typ", "JWT");
        header.put("kid", keyId);
        return header;
    }

    private static Map<String, Object> claims(Instant notBefore, Instant expiresAt) {
        return claims(notBefore, expiresAt, ISSUER, AUDIENCE, USER_ID.toString());
    }

    private static Map<String, Object> claims(
            Instant notBefore,
            Instant expiresAt,
            String issuer,
            String audience,
            String subject) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", issuer);
        claims.put("sub", subject);
        claims.put("aud", audience);
        claims.put("iat", notBefore.getEpochSecond());
        claims.put("nbf", notBefore.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", UUID.fromString("fc5ab1b9-5196-4cf4-80ab-8bd0805755d0").toString());
        return claims;
    }

    private String signedToken(Map<String, Object> header, Map<String, Object> claims) throws Exception {
        var signingInput = encoded(header) + "." + encoded(claims);
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + BASE64_URL_ENCODER.encodeToString(signature.sign());
    }

    private static String encoded(Map<String, Object> value) throws Exception {
        return BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
    }
}
