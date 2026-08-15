package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryTemplate;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityEmailTemplateFactoryTest {

    private static final EmailAddress RECIPIENT = EmailAddress.from("Developer@Example.com");

    private final IdentityEmailTemplateFactory factory =
            new IdentityEmailTemplateFactory(
                    URI.create("https://app.trail.test/verify-email"),
                    URI.create("https://app.trail.test/reset-password"),
                    Duration.ofHours(24),
                    Duration.ofMinutes(30));

    @Test
    void createsVerificationTemplateWithButtonUrlTextFallbackAndConfiguredLifetime() {
        var message = factory.verificationEmail(RECIPIENT, "secret-verification-token");

        assertThat(message.recipient()).isEqualTo(RECIPIENT);
        assertThat(message.template()).isEqualTo(EmailDeliveryTemplate.VERIFY_EMAIL);
        assertThat(message.subject()).isEqualTo("Verify your Trail account");
        assertThat(message.textBody())
                .contains("Verify your email")
                .contains("https://app.trail.test/verify-email#token=secret-verification-token")
                .contains("expires in 1 day")
                .doesNotContain("entering this verification code");
        assertThat(message.htmlBody())
                .contains(">Verify email<")
                .contains("href=\"https://app.trail.test/verify-email#token=secret-verification-token\"")
                .contains("This verification link expires in 1 day.")
                .contains("If the button does not work");
        assertThat(message.hasHtmlBody()).isTrue();
        assertThat(message.toString())
                .doesNotContain("secret-verification-token", RECIPIENT.normalized(), message.textBody(), message.htmlBody());
    }

    @Test
    void createsPasswordResetTemplateWithButtonUrlTextFallbackAndConfiguredLifetime() {
        var message = factory.passwordResetEmail(RECIPIENT, "secret-reset-token");

        assertThat(message.recipient()).isEqualTo(RECIPIENT);
        assertThat(message.template()).isEqualTo(EmailDeliveryTemplate.RESET_PASSWORD);
        assertThat(message.subject()).isEqualTo("Reset your Trail password");
        assertThat(message.textBody())
                .contains("Reset your password")
                .contains("https://app.trail.test/reset-password#token=secret-reset-token")
                .contains("expires in 30 minutes")
                .doesNotContain("entering this reset code");
        assertThat(message.htmlBody())
                .contains(">Reset password<")
                .contains("href=\"https://app.trail.test/reset-password#token=secret-reset-token\"")
                .contains("This reset link expires in 30 minutes.")
                .contains("If the button does not work");
        assertThat(message.hasHtmlBody()).isTrue();
        assertThat(message.toString())
                .doesNotContain("secret-reset-token", RECIPIENT.normalized(), message.textBody(), message.htmlBody());
    }

    @Test
    void escapesHtmlInUrlCarriedTokens() {
        var message = factory.verificationEmail(RECIPIENT, "token-<script>");

        assertThat(message.textBody()).contains("token-%3Cscript%3E");
        assertThat(message.htmlBody())
                .contains("token-%3Cscript%3E")
                .doesNotContain("token-<script>");
    }

    @Test
    void rejectsUnsafeDestinations() {
        assertThatThrownBy(
                        () ->
                                new IdentityEmailTemplateFactory(
                                        URI.create("mailto:support@example.com"),
                                        URI.create("https://app.trail.test/reset-password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verificationDestination");

        assertThatThrownBy(
                        () ->
                                new IdentityEmailTemplateFactory(
                                        URI.create("https://app.trail.test/verify-email?token=bad"),
                                        URI.create("https://app.trail.test/reset-password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verificationDestination");
    }
}
