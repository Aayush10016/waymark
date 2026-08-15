package io.github.aayush10016.trail.identity.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.application.service.IdentityEmailTemplateFactory;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryException;
import io.github.aayush10016.trail.identity.domain.port.EmailDeliveryFailureType;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LocalEmailDeliveryAdapterTest {

    private static final EmailAddress RECIPIENT = EmailAddress.from("developer@example.com");
    private static final String VERIFICATION_TOKEN = "secret-verification-token";

    private final IdentityEmailTemplateFactory templateFactory =
            new IdentityEmailTemplateFactory(
                    URI.create("https://app.trail.test/verify-email"),
                    URI.create("https://app.trail.test/reset-password"));

    @Test
    void recordsLocalDeliveryWithoutLoggingSecrets(CapturedOutput output) {
        var adapter = new LocalEmailDeliveryAdapter();
        var message = templateFactory.verificationEmail(RECIPIENT, VERIFICATION_TOKEN);

        adapter.send(message);

        assertThat(adapter.deliveredMessages()).containsExactly(message);
        assertThat(output)
                .contains("Local identity email delivery accepted template=VERIFY_EMAIL")
                .doesNotContain(VERIFICATION_TOKEN)
                .doesNotContain(RECIPIENT.normalized())
                .doesNotContain(message.subject())
                .doesNotContain(message.textBody())
                .doesNotContain(message.htmlBody())
                .doesNotContain("https://app.trail.test/verify-email");
    }

    @Test
    void recordsPasswordResetDeliveryWithoutLoggingSecrets(CapturedOutput output) {
        var adapter = new LocalEmailDeliveryAdapter();
        var resetToken = "secret-reset-token";
        var message = templateFactory.passwordResetEmail(RECIPIENT, resetToken);

        adapter.send(message);

        assertThat(adapter.deliveredMessages()).containsExactly(message);
        assertThat(output)
                .contains("Local identity email delivery accepted template=RESET_PASSWORD")
                .doesNotContain(resetToken)
                .doesNotContain(RECIPIENT.normalized())
                .doesNotContain(message.subject())
                .doesNotContain(message.textBody())
                .doesNotContain(message.htmlBody())
                .doesNotContain("https://app.trail.test/reset-password");
    }

    @Test
    void exposesTypedRetryableDeliveryFailureWithoutLoggingSecrets(CapturedOutput output) {
        var adapter = new LocalEmailDeliveryAdapter();
        var failure = EmailDeliveryException.retryable("email provider unavailable", new RuntimeException("timeout"));
        adapter.failWith(failure);

        assertThatThrownBy(() -> adapter.send(templateFactory.verificationEmail(RECIPIENT, VERIFICATION_TOKEN)))
                .isSameAs(failure)
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(candidate -> ((EmailDeliveryException) candidate).failureType())
                .isEqualTo(EmailDeliveryFailureType.RETRYABLE);

        assertThat(failure.retryable()).isTrue();
        assertThat(adapter.deliveredMessages()).isEmpty();
        assertThat(output)
                .contains("Local identity email delivery failed template=VERIFY_EMAIL retryable=true")
                .doesNotContain(VERIFICATION_TOKEN)
                .doesNotContain(RECIPIENT.normalized())
                .doesNotContain("https://app.trail.test/verify-email");
    }

    @Test
    void exposesTypedPermanentDeliveryFailure() {
        var failure = EmailDeliveryException.permanent("invalid recipient", null);

        assertThat(failure.failureType()).isEqualTo(EmailDeliveryFailureType.PERMANENT);
        assertThat(failure.retryable()).isFalse();
    }
}
