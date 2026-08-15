package io.github.aayush10016.trail.intelligence.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
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

class OpenAICompatibleAIProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    void mapsSuccessfulStructuredResponseAndCostMetadata() throws IOException {
        startServer(200, """
                {
                  "output": [
                    {"content": [{"type": "output_text", "text": "{\\"summary\\":\\"ok\\"}"}]}
                  ],
                  "usage": {"input_tokens": 1000, "output_tokens": 500, "total_tokens": 1500}
                }
                """);
        var provider = provider(endpoint(), new BigDecimal("2.00"), new BigDecimal("8.00"));

        var result = provider.generateStructured(request(Duration.ofSeconds(5)));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.response()).satisfies(response -> {
            assertThat(response.provider()).isEqualTo("openai");
            assertThat(response.model()).isEqualTo("gpt-test");
            assertThat(response.outputJson()).isEqualTo("{\"summary\":\"ok\"}");
            assertThat(response.usage().inputTokens()).isEqualTo(1000);
            assertThat(response.usage().outputTokens()).isEqualTo(500);
            assertThat(response.cost().estimatedCost()).isEqualByComparingTo("0.006");
        });

        var requestJson = OBJECT_MAPPER.readTree(lastRequestBody);
        assertThat(requestJson.path("store").asBoolean()).isFalse();
        assertThat(requestJson.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(requestJson.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(lastAuthorizationHeader).isEqualTo("Bearer test-api-key");
    }

    @Test
    void mapsProviderTimeoutStatusToRetryableFailure() throws IOException {
        startServer(504, "{}");
        var provider = provider(endpoint(), BigDecimal.ZERO, BigDecimal.ZERO);

        var result = provider.generateStructured(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(AIProviderFailureCode.PROVIDER_TIMEOUT);
        assertThat(result.failure().retryable()).isTrue();
    }

    @Test
    void mapsTransientProviderStatusToRetryableFailure() throws IOException {
        startServer(429, "{}");
        var provider = provider(endpoint(), BigDecimal.ZERO, BigDecimal.ZERO);

        var result = provider.generateStructured(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(AIProviderFailureCode.PROVIDER_UNAVAILABLE);
        assertThat(result.failure().retryable()).isTrue();
    }

    @Test
    void mapsRejectedProviderStatusToSafeFailure() throws IOException {
        startServer(400, "{}");
        var provider = provider(endpoint(), BigDecimal.ZERO, BigDecimal.ZERO);

        var result = provider.generateStructured(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(AIProviderFailureCode.PROVIDER_REJECTED);
        assertThat(result.failure().retryable()).isFalse();
    }

    @Test
    void mapsMalformedSuccessBodyToInvalidResponse() throws IOException {
        startServer(200, "{\"output\":[]}");
        var provider = provider(endpoint(), BigDecimal.ZERO, BigDecimal.ZERO);

        var result = provider.generateStructured(request(Duration.ofSeconds(5)));

        assertThat(result.failure().code()).isEqualTo(AIProviderFailureCode.INVALID_PROVIDER_RESPONSE);
        assertThat(result.failure().retryable()).isFalse();
    }

    @Test
    void appliesRequestTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            sleep();
            write(exchange, 200, "{}");
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        var provider = provider(endpoint(), BigDecimal.ZERO, BigDecimal.ZERO);

        var result = provider.generateStructured(request(Duration.ofMillis(50)));

        assertThat(result.failure().code()).isEqualTo(AIProviderFailureCode.PROVIDER_TIMEOUT);
        assertThat(result.failure().retryable()).isTrue();
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            write(exchange, statusCode, body);
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
    }

    private static OpenAICompatibleAIProvider provider(
            URI endpoint,
            BigDecimal inputCostPerMillionTokens,
            BigDecimal outputCostPerMillionTokens) {
        return new OpenAICompatibleAIProvider(
                HttpClient.newHttpClient(),
                OBJECT_MAPPER,
                endpoint,
                "test-api-key",
                new OpenAICompatibleAIProvider.OpenAICompatiblePricing(
                        inputCostPerMillionTokens,
                        outputCostPerMillionTokens));
    }

    private static StructuredAIRequest request(Duration timeout) {
        return new StructuredAIRequest(
                "gpt-test",
                "Return JSON only.",
                "Safe input",
                "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"}},\"required\":[\"summary\"],\"additionalProperties\":false}",
                timeout,
                Map.of("prompt_key", "test"));
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
