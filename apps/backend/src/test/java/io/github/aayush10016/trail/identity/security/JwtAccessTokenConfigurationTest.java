package io.github.aayush10016.trail.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtAccessTokenConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");
    private static final UUID USER_ID = UUID.fromString("894fe1d0-d797-4801-9f8d-7a17ec4555a4");

    private final JwtAccessTokenConfiguration configuration = new JwtAccessTokenConfiguration();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void readsLocalPemFilesWhenInlineJwtPemConfigurationIsAbsent(@TempDir Path tempDirectory) throws Exception {
        var keyPair = generateKeyPair();
        var privateKeyFile = tempDirectory.resolve("jwt-private.pem");
        var publicKeyFile = tempDirectory.resolve("jwt-public.pem");
        Files.writeString(privateKeyFile, privateKeyPem(keyPair));
        Files.writeString(publicKeyFile, publicKeyPem(keyPair));

        var properties = properties("", "", publicKeyFile, privateKeyFile);

        var issuer = configuration.accessTokenIssuer(properties, clock);
        var verifier = configuration.accessTokenVerifier(properties, clock);
        var issuedToken = issuer.issue(new AuthenticatedPrincipal(USER_ID));

        assertThat(issuer).isInstanceOf(JwtAccessTokenIssuer.class);
        assertThat(verifier.verify(issuedToken.token())).contains(new AuthenticatedPrincipal(USER_ID));
    }

    @Test
    void keepsCredentialIssuanceUnavailableWhenNoInlinePemOrLocalPemFileExists(@TempDir Path tempDirectory) {
        var properties = properties(
                "",
                "",
                tempDirectory.resolve("missing-public.pem"),
                tempDirectory.resolve("missing-private.pem"));

        assertThat(configuration.accessTokenIssuer(properties, clock)).isInstanceOf(UnavailableAccessTokenIssuer.class);
        assertThat(configuration.accessTokenVerifier(properties, clock)).isInstanceOf(RejectingAccessTokenVerifier.class);
    }

    @Test
    void prefersInlinePemConfigurationOverLocalPemFiles(@TempDir Path tempDirectory) throws Exception {
        var inlineKeyPair = generateKeyPair();
        var ignoredKeyPair = generateKeyPair();
        var privateKeyFile = tempDirectory.resolve("jwt-private.pem");
        var publicKeyFile = tempDirectory.resolve("jwt-public.pem");
        Files.writeString(privateKeyFile, privateKeyPem(ignoredKeyPair));
        Files.writeString(publicKeyFile, publicKeyPem(ignoredKeyPair));

        var properties = properties(publicKeyPem(inlineKeyPair), privateKeyPem(inlineKeyPair), publicKeyFile, privateKeyFile);

        var issuer = configuration.accessTokenIssuer(properties, clock);
        var verifier = configuration.accessTokenVerifier(properties, clock);
        var issuedToken = issuer.issue(new AuthenticatedPrincipal(USER_ID));

        assertThat(verifier.verify(issuedToken.token())).contains(new AuthenticatedPrincipal(USER_ID));
    }

    private static JwtAccessTokenProperties properties(
            String publicKeyPem, String privateKeyPem, Path publicKeyPath, Path privateKeyPath) {
        return new JwtAccessTokenProperties(
                "local",
                "trail-local",
                "trail-extension",
                publicKeyPem,
                privateKeyPem,
                publicKeyPath.toString(),
                privateKeyPath.toString(),
                Duration.ofMinutes(10));
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pem(String type, byte[] encoded) {
        var body = Base64.getMimeEncoder(64, System.lineSeparator().getBytes())
                .encodeToString(encoded);
        return "-----BEGIN " + type + "-----"
                + System.lineSeparator()
                + body
                + System.lineSeparator()
                + "-----END " + type + "-----"
                + System.lineSeparator();
    }
}
