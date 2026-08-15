package io.github.aayush10016.trail.identity.adapter.out.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brevo transactional email API adapter for production identity email delivery.
 *
 * <p>The adapter sends only the required identity email fields and logs only
 * provider/template/status-class metadata. It never logs API keys, recipients,
 * subjects, bodies, links, raw tokens, request bodies, or response bodies.
 */
public final class BrevoEmailDeliveryAdapter implements EmailDeliveryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrevoEmailDeliveryAdapter.class);
    private static final int BREVO_ACCEPTED_STATUS = 201;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String fromAddress;
    private final String fromName;
    private final Duration timeout;

    /**
     * Creates the Brevo adapter.
     */
    public BrevoEmailDeliveryAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI endpoint,
            String apiKey,
            String fromAddress,
            String fromName,
            Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.apiKey = requireText("apiKey", apiKey);
        this.fromAddress = requireText("fromAddress", fromAddress);
        this.fromName = requireText("fromName", fromName);
        this.timeout = validateTimeout(timeout);
    }

    @Override
    public void send(EmailDeliveryMessage message) {
        var safeMessage = Objects.requireNonNull(message, "message");
        try {
            var response = httpClient.send(httpRequest(safeMessage), HttpResponse.BodyHandlers.discarding());
            mapResponse(safeMessage, response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(safeMessage, "interrupted", true, null);
            throw EmailDeliveryException.retryable("email provider interrupted", exception);
        } catch (java.net.http.HttpTimeoutException exception) {
            logFailure(safeMessage, "timeout", true, null);
            throw EmailDeliveryException.retryable("email provider timeout", exception);
        } catch (JsonProcessingException exception) {
            logFailure(safeMessage, "request", false, null);
            throw EmailDeliveryException.permanent("email provider request could not be encoded", exception);
        } catch (IOException exception) {
            logFailure(safeMessage, "network", true, null);
            throw EmailDeliveryException.retryable("email provider unavailable", exception);
        }
    }

    private HttpRequest httpRequest(EmailDeliveryMessage message) throws JsonProcessingException {
        return HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(message)))
                .build();
    }

    private String requestBody(EmailDeliveryMessage message) throws JsonProcessingException {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sender", Map.of("name", fromName, "email", fromAddress));
        payload.put("to", List.of(Map.of("email", message.recipient().display())));
        payload.put("subject", message.subject());
        payload.put("textContent", message.textBody());
        if (message.hasHtmlBody()) {
            payload.put("htmlContent", message.htmlBody());
        }
        return objectMapper.writeValueAsString(payload);
    }

    private void mapResponse(EmailDeliveryMessage message, int statusCode) {
        if (statusCode == BREVO_ACCEPTED_STATUS) {
            LOGGER.info("Brevo identity email delivery accepted template={} statusClass=2xx", message.template());
            return;
        }
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            logFailure(message, "provider", true, statusCode);
            throw EmailDeliveryException.retryable("email provider rejected retryable delivery", null);
        }
        logFailure(message, "provider", false, statusCode);
        throw EmailDeliveryException.permanent("email provider rejected delivery", null);
    }

    private static void logFailure(
            EmailDeliveryMessage message, String category, boolean retryable, Integer statusCode) {
        if (statusCode == null) {
            LOGGER.warn(
                    "Brevo identity email delivery failed template={} category={} retryable={}",
                    message.template(),
                    category,
                    retryable);
            return;
        }
        LOGGER.warn(
                "Brevo identity email delivery failed template={} category={} statusClass={}xx retryable={}",
                message.template(),
                category,
                statusCode / 100,
                retryable);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var stripped = value.strip();
        if (stripped.contains("\r") || stripped.contains("\n")) {
            throw new IllegalArgumentException(fieldName + " must not contain line breaks");
        }
        return stripped;
    }

    private static Duration validateTimeout(Duration timeout) {
        var safeTimeout = Objects.requireNonNull(timeout, "timeout");
        if (safeTimeout.isZero() || safeTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return safeTimeout;
    }
}
