package io.github.aayush10016.trail.identity.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RS256 JWT access-token verifier for Trail API requests.
 */
public final class JwtAccessTokenVerifier implements AccessTokenVerifier {

    private static final String JWT_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String TOKEN_TYPE = "JWT";
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final PublicKey publicKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * Creates the JWT access-token verifier.
     *
     * @param publicKey RSA public verification key
     * @param keyId expected signing key ID
     * @param issuer expected issuer
     * @param audience expected audience
     * @param clock clock used for expiry validation
     */
    public JwtAccessTokenVerifier(PublicKey publicKey, String keyId, String issuer, String audience, Clock clock) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.keyId = validateText("keyId", keyId);
        this.issuer = validateText("issuer", issuer);
        this.audience = validateText("audience", audience);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<AuthenticatedPrincipal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        var parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }

        try {
            var header = decodeJson(parts[0]);
            var claims = decodeJson(parts[1]);
            var signingInput = parts[0] + "." + parts[1];

            if (!headerMatches(header) || !claimsMatch(claims) || !signatureMatches(signingInput, parts[2])) {
                return Optional.empty();
            }

            return principalFromClaims(claims);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(value), JSON_MAP);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Invalid JWT JSON segment", exception);
        }
    }

    private boolean headerMatches(Map<String, Object> header) {
        return JwtAccessTokenIssuer.ALGORITHM.equals(header.get("alg"))
                && TOKEN_TYPE.equals(header.get("typ"))
                && keyId.equals(header.get("kid"));
    }

    private boolean claimsMatch(Map<String, Object> claims) {
        var now = clock.instant();
        return issuer.equals(claims.get("iss"))
                && audienceMatches(claims.get("aud"))
                && instantClaim(claims, "nbf").map(notBefore -> !now.isBefore(notBefore)).orElse(false)
                && instantClaim(claims, "exp").map(expiresAt -> now.isBefore(expiresAt)).orElse(false);
    }

    private boolean audienceMatches(Object value) {
        if (value instanceof String singleAudience) {
            return audience.equals(singleAudience);
        }
        if (value instanceof List<?> audiences) {
            return audiences.contains(audience);
        }
        return false;
    }

    private Optional<Instant> instantClaim(Map<String, Object> claims, String name) {
        var value = claims.get(name);
        if (value instanceof Number numericValue) {
            return Optional.of(Instant.ofEpochSecond(numericValue.longValue()));
        }
        return Optional.empty();
    }

    private boolean signatureMatches(String signingInput, String encodedSignature) {
        try {
            var signature = Signature.getInstance(JWT_SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.verify(BASE64_URL_DECODER.decode(encodedSignature));
        } catch (InvalidKeyException | SignatureException | IllegalArgumentException exception) {
            return false;
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JWT verification algorithm is unavailable", exception);
        }
    }

    private Optional<AuthenticatedPrincipal> principalFromClaims(Map<String, Object> claims) {
        var subject = claims.get("sub");
        if (!(subject instanceof String rawSubject) || rawSubject.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new AuthenticatedPrincipal(UUID.fromString(rawSubject)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String validateText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
