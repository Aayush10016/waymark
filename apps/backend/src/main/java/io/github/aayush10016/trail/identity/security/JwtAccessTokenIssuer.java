package io.github.aayush10016.trail.identity.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * RS256 JWT access-token issuer.
 */
public final class JwtAccessTokenIssuer implements AccessTokenIssuer {

    /**
     * JWT algorithm used by Trail v1.1 access tokens.
     */
    public static final String ALGORITHM = "RS256";

    private static final String JWT_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String TOKEN_TYPE = "JWT";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTimeToLive;
    private final Clock clock;
    private final Supplier<UUID> tokenIdSupplier;
    private final ObjectMapper objectMapper;

    /**
     * Creates the JWT access-token issuer.
     *
     * @param privateKey RSA private signing key
     * @param keyId signing key ID
     * @param issuer expected token issuer
     * @param audience expected token audience
     * @param accessTokenTimeToLive access-token lifetime
     * @param clock application clock
     * @param tokenIdSupplier token ID supplier
     */
    public JwtAccessTokenIssuer(
            PrivateKey privateKey,
            String keyId,
            String issuer,
            String audience,
            Duration accessTokenTimeToLive,
            Clock clock,
            Supplier<UUID> tokenIdSupplier) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.keyId = validateText("keyId", keyId);
        this.issuer = validateText("issuer", issuer);
        this.audience = validateText("audience", audience);
        this.accessTokenTimeToLive = validateTimeToLive(accessTokenTimeToLive);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenIdSupplier = Objects.requireNonNull(tokenIdSupplier, "tokenIdSupplier");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public IssuedAccessToken issue(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(accessTokenTimeToLive);

        var header = new LinkedHashMap<String, Object>();
        header.put("alg", ALGORITHM);
        header.put("typ", TOKEN_TYPE);
        header.put("kid", keyId);

        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", issuer);
        claims.put("sub", principal.userId().toString());
        claims.put("aud", audience);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("nbf", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", tokenIdSupplier.get().toString());

        var signingInput = base64UrlJson(header) + "." + base64UrlJson(claims);
        return IssuedAccessToken.bearer(signingInput + "." + sign(signingInput), expiresAt);
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize JWT segment", exception);
        }
    }

    private String sign(String signingInput) {
        try {
            var signature = Signature.getInstance(JWT_SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL_ENCODER.encodeToString(signature.sign());
        } catch (InvalidKeyException | SignatureException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JWT signing algorithm is unavailable", exception);
        }
    }

    private static String validateText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static Duration validateTimeToLive(Duration accessTokenTimeToLive) {
        Objects.requireNonNull(accessTokenTimeToLive, "accessTokenTimeToLive");
        if (accessTokenTimeToLive.isZero() || accessTokenTimeToLive.isNegative()) {
            throw new IllegalArgumentException("accessTokenTimeToLive must be positive");
        }
        return accessTokenTimeToLive;
    }
}
