package io.github.aayush10016.trail.intelligence.adapter.out.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAICompatibleEmbeddingProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FINGERPRINT = "c".repeat(64);

    private HttpServer server;
    private String lastRequestBody;
    private String lastAuthorizationHeader;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsSuccessfulEmbeddingResponseAndCostMetadata() throws IOException {
        startServer(200, """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "embedding": [0.1, 0.2, 0.3], "index": 0}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 1000, "total_tokens": 1000}
                }
                """);
        var provider = provider(endpoint(), new BigDecimal("2.00"));

        var result = provider.embed(request(Duration.ofSeconds(5)));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.response()).satisfies(response -> {
            assertThat(response.provider()).isEqualTo("openai");
            assertThat(response.model()).isEqualTo("text-embedding-3-small");
            assertThat(response.dimensions()).isEqualTo(3);
            assertThat(response.vector()).containsExactly(0.1, 0.2, 0.3);
            assertThat(response.usage().inputTokens()).isEqualTo(1000);
            assertThat(response.cost().estimatedCost()).isEqualByComparingTo("0.002");
        });

        var requestJson = OBJECT_MAPPER.readTree(lastRequestBody);
        assertThat(requestJson.path("model").asText()).isEqualTo("text-embedding-3-small");
        assertThat(requestJson.path("input").asText()).isEqualTo("Spring Security CORS solution");
        assertThat(requestJson.path("dimensions").asInt()).isEqualTo(3);
        assertThat(requestJson.path("encoding_format").asText()).isEqualTo("float");
        assertThat(lastAuthorizationHeader).isEqualTo("Bearer test-api-key");
    }

    @Test
    void mapsProviderTimeoutStatusToRetryableFailure() throws IOException {
        startServer(504, "{}");
        var result = provider(endpoint(), BigDecimal.ZERO).embed(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT);
        assertThat(result.failure().retryable()).isTrue();
    }

    @Test
    void mapsTransientProviderStatusToRetryableFailure() throws IOException {
        startServer(429, "{}");
        var result = provider(endpoint(), BigDecimal.ZERO).embed(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.PROVIDER_UNAVAILABLE);
        assertThat(result.failure().retryable()).isTrue();
    }

    @Test
    void mapsRejectedProviderStatusToSafeFailure() throws IOException {
        startServer(400, "{}");
        var result = provider(endpoint(), BigDecimal.ZERO).embed(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.PROVIDER_REJECTED);
        assertThat(result.failure().retryable()).isFalse();
    }

    @Test
    void mapsMalformedSuccessBodyToInvalidResponse() throws IOException {
        startServer(200, "{\"data\":[]}");
        var result = provider(endpoint(), BigDecimal.ZERO).embed(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.INVALID_PROVIDER_RESPONSE);
        assertThat(result.failure().retryable()).isFalse();
    }

    @Test
    void appliesRequestTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            sleep();
            write(exchange, 200, "{}");
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        var result = provider(endpoint(), BigDecimal.ZERO).embed(request(Duration.ofMillis(50)));

        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT);
        assertThat(result.failure().retryable()).isTrue();
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            write(exchange, statusCode, body);
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings");
    }

    private static OpenAICompatibleEmbeddingProvider provider(URI endpoint, BigDecimal inputCostPerMillionTokens) {
        return new OpenAICompatibleEmbeddingProvider(
                HttpClient.newHttpClient(),
                OBJECT_MAPPER,
                endpoint,
                "test-api-key",
                new OpenAICompatibleEmbeddingProvider.OpenAICompatibleEmbeddingPricing(inputCostPerMillionTokens));
    }

    private static EmbeddingRequest request(Duration timeout) {
        return new EmbeddingRequest(
                "text-embedding-3-small",
                "Spring Security CORS solution",
                3,
                FINGERPRINT,
                "embedding-policy-v1",
                timeout,
                Map.of("source", "test"));
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
