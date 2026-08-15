package io.github.aayush10016.trail.identity.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validated identity email delivery provider configuration.
 */
@ConfigurationProperties("trail.identity.email")
public record IdentityEmailDeliveryProperties(
        IdentityEmailProviderSelection provider,
        String fromAddress,
        String fromName,
        Duration timeout,
        Brevo brevo) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_FROM_NAME = "Trail";

    /**
     * Creates validated identity email delivery properties.
     */
    public IdentityEmailDeliveryProperties {
        provider = provider == null ? IdentityEmailProviderSelection.LOCAL : provider;
        fromAddress = fromAddress == null ? "" : fromAddress.strip();
        fromName = fromName == null ? DEFAULT_FROM_NAME : fromName.strip();
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        brevo = brevo == null ? Brevo.defaults() : brevo;

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("trail.identity.email.timeout must be positive");
        }
        if (provider == IdentityEmailProviderSelection.BREVO) {
            requireText("trail.identity.email.from-address", fromAddress);
            requireText("trail.identity.email.from-name", fromName);
            requireText("trail.identity.email.brevo.api-key", brevo.apiKey());
        }
    }

    /**
     * Returns the configured email request timeout or default.
     */
    public Duration timeoutOrDefault() {
        return timeout;
    }

    /**
     * Brevo provider-specific configuration.
     */
    public record Brevo(URI endpoint, String apiKey) {

        private static final URI DEFAULT_ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

        /**
         * Creates validated Brevo configuration.
         */
        public Brevo {
            endpoint = endpoint == null ? DEFAULT_ENDPOINT : endpoint;
            apiKey = apiKey == null ? "" : apiKey.strip();
            validateEndpoint(endpoint);
        }

        private static Brevo defaults() {
            return new Brevo(DEFAULT_ENDPOINT, "");
        }
    }

    private static void requireText(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " is required when trail.identity.email.provider=brevo");
        }
        if (value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException(propertyName + " must not contain line breaks");
        }
    }

    private static void validateEndpoint(URI endpoint) {
        if (endpoint.getScheme() == null || endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new IllegalArgumentException("trail.identity.email.brevo.endpoint must be an absolute HTTP URI");
        }
        var scheme = endpoint.getScheme().toLowerCase(java.util.Locale.ROOT);
        if ("https".equals(scheme)) {
            return;
        }
        if ("http".equals(scheme) && isLoopbackHost(endpoint.getHost())) {
            return;
        }
        throw new IllegalArgumentException(
                "trail.identity.email.brevo.endpoint must use HTTPS outside local loopback tests");
    }

    private static boolean isLoopbackHost(String host) {
        var normalized = host.strip().toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }
}
