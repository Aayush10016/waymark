package io.github.aayush10016.trail.identity.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.aayush10016.trail.identity.application.service.IdentityEmailTemplateFactory;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryFailureType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BrevoEmailDeliveryAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_KEY = "test-brevo-api-key";
    private static final String FROM_ADDRESS = "noreply@example.test";
    private static final String FROM_NAME = "Trail";
    private static final String RECIPIENT = "developer@example.com";
    private static final String TOKEN = "secret-token-canary";

    private final IdentityEmailTemplateFactory templateFactory =
            new IdentityEmailTemplateFactory(
                    URI.create("https://app.trail.test/verify"),
                    URI.create("https://app.trail.test/reset"));

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String lastMethod;
    private String lastRequestPath;
    private String lastRequestBody;
    private String lastApiKeyHeader;
    private String lastAuthorizationHeader;
    private String lastContentTypeHeader;

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
    void sendsExpectedSafeBrevoRequest(CapturedOutput output) throws Exception {
        startServer(201, "{\"messageId\":\"provider-id\"}");
        var message = verificationMessage();

        provider(Duration.ofSeconds(5)).send(message);

        var requestJson = OBJECT_MAPPER.readTree(lastRequestBody);
        assertThat(lastMethod).isEqualTo("POST");
        assertThat(lastRequestPath).isEqualTo("/v3/smtp/email");
        assertThat(lastApiKeyHeader).isEqualTo(API_KEY);
        assertThat(lastAuthorizationHeader).isNull();
        assertThat(lastContentTypeHeader).isEqualTo("application/json");
        assertThat(requestJson.path("sender").path("name").asText()).isEqualTo(FROM_NAME);
        assertThat(requestJson.path("sender").path("email").asText()).isEqualTo(FROM_ADDRESS);
        assertThat(requestJson.path("to").get(0).path("email").asText()).isEqualTo(RECIPIENT);
        assertThat(requestJson.path("subject").asText()).isEqualTo(message.subject());
        assertThat(requestJson.path("textContent").asText()).isEqualTo(message.textBody());
        assertThat(requestJson.path("htmlContent").asText()).isEqualTo(message.htmlBody());
        assertThat(requestJson.properties().stream().map(Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(Set.of("sender", "to", "subject", "textContent", "htmlContent"));
        assertThat(requestJson.path("sender").properties().stream().map(Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(Set.of("name", "email"));
        assertThat(requestJson.path("to").get(0).properties().stream().map(Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(Set.of("email"));
        assertThat(output)
                .contains("Brevo identity email delivery accepted template=VERIFY_EMAIL statusClass=2xx")
                .doesNotContain(API_KEY)
                .doesNotContain(RECIPIENT)
                .doesNotContain(TOKEN)
                .doesNotContain(message.subject())
                .doesNotContain(message.textBody())
                .doesNotContain(message.htmlBody())
                .doesNotContain("provider-id");
    }

    @Test
    void mapsRateLimitRequestTimeoutAndServerFailuresToRetryable(CapturedOutput output) throws Exception {
        startServer(429, "{\"message\":\"rate limit " + TOKEN + "\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(candidate -> ((EmailDeliveryException) candidate).failureType())
                .isEqualTo(EmailDeliveryFailureType.RETRYABLE);

        assertThat(output).doesNotContain(API_KEY).doesNotContain(RECIPIENT).doesNotContain(TOKEN);

        stopServer();
        startServer(503, "{\"message\":\"provider down " + TOKEN + "\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(candidate -> ((EmailDeliveryException) candidate).failureType())
                .isEqualTo(EmailDeliveryFailureType.RETRYABLE);

        stopServer();
        startServer(408, "{\"message\":\"request timeout " + TOKEN + "\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(candidate -> ((EmailDeliveryException) candidate).failureType())
                .isEqualTo(EmailDeliveryFailureType.RETRYABLE);
        assertThat(output).doesNotContain(API_KEY).doesNotContain(RECIPIENT).doesNotContain(TOKEN);
    }

    @Test
    void mapsClientRejectionToPermanentWithoutLeakingResponseBody(CapturedOutput output) throws Exception {
        startServer(400, "{\"message\":\"invalid recipient " + RECIPIENT + " " + TOKEN + "\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(exception -> {
                    var deliveryException = (EmailDeliveryException) exception;
                    assertThat(deliveryException.failureType()).isEqualTo(EmailDeliveryFailureType.PERMANENT);
                    assertThat(deliveryException.getMessage())
                            .doesNotContain(RECIPIENT)
                            .doesNotContain(TOKEN)
                            .doesNotContain(API_KEY);
                });

        assertThat(output).doesNotContain(API_KEY).doesNotContain(RECIPIENT).doesNotContain(TOKEN);
    }

    @Test
    void treatsNonBrevoSuccessStatusAsPermanentFailure(CapturedOutput output) throws Exception {
        startServer(200, "{\"messageId\":\"provider-id\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(candidate -> ((EmailDeliveryException) candidate).failureType())
                .isEqualTo(EmailDeliveryFailureType.PERMANENT);

        assertThat(output)
                .doesNotContain(API_KEY)
                .doesNotContain(RECIPIENT)
                .doesNotContain(TOKEN)
                .doesNotContain("provider-id");
    }

    @Test
    void mapsTimeoutToRetryableWithoutLeakingSecrets(CapturedOutput output) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            sleep();
            write(exchange, 201, "{}");
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();

        assertThatThrownBy(() -> provider(Duration.ofMillis(50)).send(verificationMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(exception -> {
                    var deliveryException = (EmailDeliveryException) exception;
                    assertThat(deliveryException.failureType()).isEqualTo(EmailDeliveryFailureType.RETRYABLE);
                    assertThat(deliveryException.getMessage())
                            .doesNotContain(API_KEY)
                            .doesNotContain(RECIPIENT)
                            .doesNotContain(TOKEN);
                });

        assertThat(output).doesNotContain(API_KEY).doesNotContain(RECIPIENT).doesNotContain(TOKEN);
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            lastMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().getPath();
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastApiKeyHeader = exchange.getRequestHeaders().getFirst("api-key");
            lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastContentTypeHeader = exchange.getRequestHeaders().getFirst("Content-Type");
            write(exchange, statusCode, body);
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private BrevoEmailDeliveryAdapter provider(Duration timeout) {
        return new BrevoEmailDeliveryAdapter(
                HttpClient.newHttpClient(),
                OBJECT_MAPPER,
                endpoint(),
                API_KEY,
                FROM_ADDRESS,
                FROM_NAME,
                timeout);
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
    }

    private EmailDeliveryMessage verificationMessage() {
        return templateFactory.verificationEmail(EmailAddress.from(RECIPIENT), TOKEN);
    }

    private static void write(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
