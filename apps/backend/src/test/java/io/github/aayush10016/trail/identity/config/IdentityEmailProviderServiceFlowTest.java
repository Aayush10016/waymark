package io.github.aayush10016.trail.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.aayush10016.trail.identity.application.command.RequestEmailVerificationCommand;
import io.github.aayush10016.trail.identity.application.command.RequestPasswordResetCommand;
import io.github.aayush10016.trail.identity.application.dto.EmailVerificationResult;
import io.github.aayush10016.trail.identity.application.dto.PasswordResetRequestResult;
import io.github.aayush10016.trail.identity.application.service.EmailVerificationService;
import io.github.aayush10016.trail.identity.application.service.IdentityEmailTemplateFactory;
import io.github.aayush10016.trail.identity.application.service.PasswordResetRequestService;
import io.github.aayush10016.trail.identity.adapter.out.email.LocalEmailDeliveryAdapter;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import io.github.aayush10016.trail.identity.domain.port.IdentityRateLimiter;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import io.github.aayush10016.trail.identity.security.Sha256EmailActionTokenHashingPolicy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdentityEmailProviderServiceFlowTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final UUID TOKEN_ID = UUID.fromString("05883a93-fbf0-4e26-8be1-9e2dd84d74ec");
    private static final EmailActionTokenHashingPolicy HASHING_POLICY = new Sha256EmailActionTokenHashingPolicy();

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final List<String> requestBodies = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void verificationAndPasswordResetRequestsUseSelectedBrevoEmailPort() throws Exception {
        startServer();
        var emailPort = new IdentityConfiguration().emailDeliveryPort(
                new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.BREVO,
                        "noreply@example.test",
                        "Trail",
                        Duration.ofSeconds(5),
                        new IdentityEmailDeliveryProperties.Brevo(endpoint(), "test-api-key")),
                new LocalEmailDeliveryAdapter(),
                new ObjectMapper());
        var templateFactory = new IdentityEmailTemplateFactory(
                URI.create("https://app.trail.test/verify"),
                URI.create("https://app.trail.test/reset"));
        var verificationRepository = new StoringVerificationRepository();
        var passwordResetRepository = new StoringPasswordResetRepository();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);

        var verificationService = new EmailVerificationService(
                verificationRepository,
                () -> "verification-token-canary",
                HASHING_POLICY,
                emailPort,
                templateFactory,
                new AllowingRateLimiter(),
                clock,
                () -> TOKEN_ID,
                Duration.ofHours(1));
        var passwordResetService = new PasswordResetRequestService(
                passwordResetRepository,
                () -> "reset-token-canary",
                HASHING_POLICY,
                emailPort,
                templateFactory,
                new AllowingRateLimiter(),
                clock,
                () -> TOKEN_ID,
                Duration.ofHours(1));

        assertThat(verificationService.requestVerification(
                        new RequestEmailVerificationCommand("developer@example.com")))
                .isEqualTo(EmailVerificationResult.accepted());
        assertThat(passwordResetService.requestReset(
                        new RequestPasswordResetCommand("developer@example.com")))
                .isEqualTo(PasswordResetRequestResult.accepted());

        assertThat(requestBodies).hasSize(2);
        assertThat(requestBodies.get(0)).contains("verification-token-canary");
        assertThat(requestBodies.get(1)).contains("reset-token-canary");
        assertThat(verificationRepository.storedTokens()).hasSize(1);
        assertThat(passwordResetRepository.storedTokens()).hasSize(1);
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange);
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
    }

    private static void write(HttpExchange exchange) throws IOException {
        var bytes = "{\"messageId\":\"email-id\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(201, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class StoringVerificationRepository implements EmailVerificationTokenRepository {

        private final List<StoredEmailVerificationToken> storedTokens = new ArrayList<>();

        @Override
        public StoreResult storeForPendingEmail(EmailAddress email, StoredEmailVerificationToken token) {
            storedTokens.add(token);
            return StoreResult.STORED;
        }

        @Override
        public ConsumeResult consume(EmailActionTokenHash tokenHash, Instant consumedAt) {
            return ConsumeResult.NOT_ACTIVE;
        }

        List<StoredEmailVerificationToken> storedTokens() {
            return List.copyOf(storedTokens);
        }
    }

    private static final class StoringPasswordResetRepository implements PasswordResetTokenRepository {

        private final List<StoredPasswordResetToken> storedTokens = new ArrayList<>();

        @Override
        public StoreResult storeForActiveEmail(EmailAddress email, StoredPasswordResetToken token) {
            storedTokens.add(token);
            return StoreResult.stored(email);
        }

        @Override
        public ConsumeResult consume(
                EmailActionTokenHash tokenHash,
                io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord newPasswordHash,
                Instant consumedAt) {
            return ConsumeResult.notActive();
        }

        List<StoredPasswordResetToken> storedTokens() {
            return List.copyOf(storedTokens);
        }
    }

    private static final class AllowingRateLimiter implements IdentityRateLimiter {

        @Override
        public boolean isAllowed(IdentityRateLimitAttempt attempt) {
            return true;
        }
    }
}
