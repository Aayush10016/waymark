package io.github.aayush10016.trail.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityEmailDeliveryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void localProviderIsDefaultAndNeedsNoBrevoSecrets() {
        var properties = new IdentityEmailDeliveryProperties(null, null, null, null, null);

        assertThat(properties.provider()).isEqualTo(IdentityEmailProviderSelection.LOCAL);
        assertThat(properties.fromAddress()).isEmpty();
        assertThat(properties.fromName()).isEqualTo("Trail");
        assertThat(properties.timeoutOrDefault()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.brevo().endpoint()).isEqualTo(URI.create("https://api.brevo.com/v3/smtp/email"));
        assertThat(properties.brevo().apiKey()).isEmpty();
    }

    @Test
    void rejectsBrevoSelectionWithoutApiKeyOrFromAddress() {
        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.BREVO,
                        "Trail <noreply@example.com>",
                        "Trail",
                        Duration.ofSeconds(5),
                        new IdentityEmailDeliveryProperties.Brevo(
                                URI.create("https://api.brevo.com/v3/smtp/email"),
                                "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trail.identity.email.brevo.api-key");

        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.BREVO,
                        "",
                        "Trail",
                        Duration.ofSeconds(5),
                        new IdentityEmailDeliveryProperties.Brevo(
                                URI.create("https://api.brevo.com/v3/smtp/email"),
                                "test-api-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trail.identity.email.from-address");

        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.BREVO,
                        "noreply@example.test",
                        "",
                        Duration.ofSeconds(5),
                        new IdentityEmailDeliveryProperties.Brevo(
                                URI.create("https://api.brevo.com/v3/smtp/email"),
                                "test-api-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trail.identity.email.from-name");
    }

    @Test
    void validatesEndpointSafety() {
        assertThat(new IdentityEmailDeliveryProperties.Brevo(
                        URI.create("http://127.0.0.1:9999/emails"),
                        "test-api-key")
                .endpoint())
                .hasScheme("http");

        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties.Brevo(
                        URI.create("http://api.brevo.com/v3/smtp/email"),
                        "test-api-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties.Brevo(
                        URI.create("file:///tmp/brevo"),
                        "test-api-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute HTTP URI");
    }

    @Test
    void validatesTimeout() {
        assertThatThrownBy(() -> new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.LOCAL,
                        null,
                        null,
                        Duration.ZERO,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trail.identity.email.timeout");
    }

    @Test
    void rejectsInvalidProviderValuesThroughSpringBinding() {
        contextRunner
                .withPropertyValues("trail.identity.email.provider=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(IdentityEmailDeliveryProperties.class)
    static class PropertiesConfiguration {}
}
