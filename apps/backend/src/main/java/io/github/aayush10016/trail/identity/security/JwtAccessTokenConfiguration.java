package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Access-token verification wiring.
 */
@Configuration
@EnableConfigurationProperties(JwtAccessTokenProperties.class)
public class JwtAccessTokenConfiguration {

    /**
     * Creates the access-token issuer.
     */
    @Bean
    public AccessTokenIssuer accessTokenIssuer(JwtAccessTokenProperties properties, Clock clock) {
        var privateKeyPem = privateKeyPem(properties);
        if (privateKeyPem.isEmpty()) {
            return new UnavailableAccessTokenIssuer();
        }

        return new JwtAccessTokenIssuer(
                parsePrivateKey(privateKeyPem.get()),
                required("trail.identity.jwt.key-id", properties.keyId()),
                required("trail.identity.jwt.issuer", properties.issuer()),
                required("trail.identity.jwt.audience", properties.audience()),
                properties.accessTokenTimeToLiveOrDefault(),
                clock,
                UUID::randomUUID);
    }

    /**
     * Creates the access-token verifier.
     */
    @Bean
    public AccessTokenVerifier accessTokenVerifier(JwtAccessTokenProperties properties, Clock clock) {
        var publicKeyPem = publicKeyPem(properties);
        if (publicKeyPem.isEmpty()) {
            return new RejectingAccessTokenVerifier();
        }

        return new JwtAccessTokenVerifier(
                parsePublicKey(publicKeyPem.get()),
                required("trail.identity.jwt.key-id", properties.keyId()),
                required("trail.identity.jwt.issuer", properties.issuer()),
                required("trail.identity.jwt.audience", properties.audience()),
                clock);
    }

    private Optional<String> privateKeyPem(JwtAccessTokenProperties properties) {
        if (properties.hasPrivateKey()) {
            return Optional.of(properties.privateKeyPem());
        }
        return readPemFileIfPresent(properties.privateKeyPath(), "JWT private key");
    }

    private Optional<String> publicKeyPem(JwtAccessTokenProperties properties) {
        if (properties.hasPublicKey()) {
            return Optional.of(properties.publicKeyPem());
        }
        return readPemFileIfPresent(properties.publicKeyPath(), "JWT public key");
    }

    private Optional<String> readPemFileIfPresent(String configuredPath, String description) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }

        var path = Path.of(configuredPath.strip());
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            var pem = Files.readString(path);
            if (pem.isBlank()) {
                throw new IllegalStateException("Configured " + description + " file is empty");
            }
            return Optional.of(pem);
        } catch (IOException exception) {
            throw new IllegalStateException("Configured " + description + " file cannot be read", exception);
        }
    }

    private PublicKey parsePublicKey(String publicKeyPem) {
        var normalized = publicKeyPem
                .replace(pemBoundary("PUBLIC", true), "")
                .replace(pemBoundary("PUBLIC", false), "")
                .replaceAll("\\s", "");
        try {
            var keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(normalized));
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Configured JWT public key is invalid", exception);
        }
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) {
        var normalized = privateKeyPem
                .replace(pemBoundary("PRIVATE", true), "")
                .replace(pemBoundary("PRIVATE", false), "")
                .replaceAll("\\s", "");
        try {
            var keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized));
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Configured JWT private key is invalid", exception);
        }
    }

    private String pemBoundary(String keyType, boolean begin) {
        return "-----" + (begin ? "BEGIN" : "END") + " " + keyType + " KEY-----";
    }

    private String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when JWT verification is configured");
        }
        return value.strip();
    }
}
