package io.github.aayush10016.trail.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.identity.adapter.out.email.BrevoEmailDeliveryAdapter;
import io.github.aayush10016.trail.identity.adapter.out.email.LocalEmailDeliveryAdapter;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityEmailProviderSelectionTest {

    private final IdentityConfiguration configuration = new IdentityConfiguration();

    @Test
    void selectsLocalEmailDeliveryByDefault() {
        var local = new LocalEmailDeliveryAdapter();

        var selected = configuration.emailDeliveryPort(
                new IdentityEmailDeliveryProperties(null, null, null, null, null),
                local,
                new ObjectMapper());

        assertThat(selected).isSameAs(local);
    }

    @Test
    void selectsBrevoEmailDeliveryWhenConfigured() {
        var selected = configuration.emailDeliveryPort(
                new IdentityEmailDeliveryProperties(
                        IdentityEmailProviderSelection.BREVO,
                        "noreply@example.test",
                        "Trail",
                        Duration.ofSeconds(5),
                        new IdentityEmailDeliveryProperties.Brevo(
                                URI.create("https://api.brevo.com/v3/smtp/email"),
                                "test-api-key")),
                new LocalEmailDeliveryAdapter(),
                new ObjectMapper());

        assertThat(selected).isInstanceOf(BrevoEmailDeliveryAdapter.class);
    }
}
