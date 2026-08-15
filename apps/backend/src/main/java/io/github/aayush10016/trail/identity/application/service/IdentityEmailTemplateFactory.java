package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryMessage;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryTemplate;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates identity email messages from configured application destinations and
 * one-time raw tokens.
 */
public final class IdentityEmailTemplateFactory {

    private final URI verificationDestination;
    private final URI passwordResetDestination;
    private final Duration verificationTokenTimeToLive;
    private final Duration passwordResetTokenTimeToLive;

    /**
     * Creates the identity email template factory.
     *
     * @param verificationDestination destination where users enter verification tokens
     * @param passwordResetDestination destination where users enter password reset tokens
     */
    public IdentityEmailTemplateFactory(URI verificationDestination, URI passwordResetDestination) {
        this(verificationDestination, passwordResetDestination, Duration.ofHours(24), Duration.ofHours(1));
    }

    /**
     * Creates the identity email template factory.
     *
     * @param verificationDestination destination that consumes verification tokens from the URL
     * @param passwordResetDestination destination that consumes password reset tokens from the URL
     * @param verificationTokenTimeToLive configured verification token lifetime
     * @param passwordResetTokenTimeToLive configured password reset token lifetime
     */
    public IdentityEmailTemplateFactory(
            URI verificationDestination,
            URI passwordResetDestination,
            Duration verificationTokenTimeToLive,
            Duration passwordResetTokenTimeToLive) {
        this.verificationDestination = validateDestination("verificationDestination", verificationDestination);
        this.passwordResetDestination = validateDestination("passwordResetDestination", passwordResetDestination);
        this.verificationTokenTimeToLive = validateDuration("verificationTokenTimeToLive", verificationTokenTimeToLive);
        this.passwordResetTokenTimeToLive = validateDuration("passwordResetTokenTimeToLive", passwordResetTokenTimeToLive);
    }

    /**
     * Creates a verification email.
     *
     * @param recipient recipient email address
     * @param token raw one-time verification token
     * @return email message
     */
    public EmailDeliveryMessage verificationEmail(EmailAddress recipient, String token) {
        var safeToken = validateToken(token);
        var verificationUrl = actionUrl(verificationDestination, safeToken);
        var expiration = formatDuration(verificationTokenTimeToLive);
        var textBody =
                """
                Trail

                Verify your email

                Thanks for creating your Trail account.
                Verify your email address to finish setting up your account.

                Verify email:
                %s

                This verification link expires in %s.

                If you did not create a Trail account, ignore this email.
                """
                        .formatted(verificationUrl, expiration);
        var htmlBody = identityHtml(
                "Verify your email",
                "Thanks for creating your Trail account.",
                "Verify your email address to finish setting up your account.",
                "Verify email",
                verificationUrl,
                "This verification link expires in " + expiration + ".",
                "If you didn't create a Trail account, you can ignore this email.");
        return new EmailDeliveryMessage(
                recipient, EmailDeliveryTemplate.VERIFY_EMAIL, "Verify your Trail account", textBody, htmlBody);
    }

    /**
     * Creates a password reset email.
     *
     * @param recipient recipient email address
     * @param token raw one-time password reset token
     * @return email message
     */
    public EmailDeliveryMessage passwordResetEmail(EmailAddress recipient, String token) {
        var safeToken = validateToken(token);
        var resetUrl = actionUrl(passwordResetDestination, safeToken);
        var expiration = formatDuration(passwordResetTokenTimeToLive);
        var textBody =
                """
                Trail

                Reset your password

                We received a request to reset your Trail password.

                Reset password:
                %s

                This reset link expires in %s.

                If you did not request a password reset, ignore this email.
                """
                        .formatted(resetUrl, expiration);
        var htmlBody = identityHtml(
                "Reset your password",
                "We received a request to reset your Trail password.",
                "",
                "Reset password",
                resetUrl,
                "This reset link expires in " + expiration + ".",
                "If you didn't request a password reset, you can ignore this email.");
        return new EmailDeliveryMessage(
                recipient, EmailDeliveryTemplate.RESET_PASSWORD, "Reset your Trail password", textBody, htmlBody);
    }

    private static URI validateDestination(String fieldName, URI destination) {
        Objects.requireNonNull(destination, fieldName);
        var scheme = destination.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(fieldName + " must use http or https");
        }
        if (destination.getHost() == null || destination.getHost().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must include a host");
        }
        if (destination.getRawQuery() != null || destination.getRawFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must not include query or fragment");
        }
        return destination;
    }

    private static String validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return token.strip();
    }

    private static Duration validateDuration(String fieldName, Duration duration) {
        var safeDuration = Objects.requireNonNull(duration, fieldName);
        if (safeDuration.isZero() || safeDuration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return safeDuration;
    }

    private static URI actionUrl(URI destination, String token) {
        return URI.create(destination + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private static String formatDuration(Duration duration) {
        if (duration.toDaysPart() > 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0) {
            return duration.toDaysPart() == 1 ? "1 day" : duration.toDaysPart() + " days";
        }
        if (duration.toHoursPart() > 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            var totalHours = duration.toHours();
            return totalHours == 1 ? "1 hour" : totalHours + " hours";
        }
        if (duration.toMinutesPart() > 0 && duration.toSecondsPart() == 0) {
            var totalMinutes = duration.toMinutes();
            return totalMinutes == 1 ? "1 minute" : totalMinutes + " minutes";
        }
        var totalSeconds = duration.toSeconds();
        return totalSeconds == 1 ? "1 second" : totalSeconds + " seconds";
    }

    private static String identityHtml(
            String heading,
            String firstParagraph,
            String secondParagraph,
            String actionLabel,
            URI actionUrl,
            String expiration,
            String ignoreCopy) {
        var safeHeading = escapeHtml(heading);
        var safeFirstParagraph = escapeHtml(firstParagraph);
        var safeSecondParagraph = escapeHtml(secondParagraph);
        var safeActionLabel = escapeHtml(actionLabel);
        var safeActionUrl = escapeHtml(actionUrl.toString());
        var safeExpiration = escapeHtml(expiration);
        var safeIgnoreCopy = escapeHtml(ignoreCopy);
        var secondaryParagraph = safeSecondParagraph.isBlank()
                ? ""
                : "<p style=\"margin:0 0 24px;color:#3f4b5f;font-size:15px;line-height:1.6;\">" + safeSecondParagraph + "</p>";
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f5f7fb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#111827;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f5f7fb;padding:32px 16px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #e5e7eb;border-radius:20px;overflow:hidden;">
                            <tr>
                              <td style="padding:32px 32px 8px;">
                                <p style="margin:0 0 18px;color:#6d5dfc;font-size:13px;font-weight:700;letter-spacing:.14em;text-transform:uppercase;">Trail</p>
                                <h1 style="margin:0 0 16px;color:#111827;font-size:28px;line-height:1.15;letter-spacing:-.03em;">%s</h1>
                                <p style="margin:0 0 8px;color:#3f4b5f;font-size:15px;line-height:1.6;">%s</p>
                                %s
                                <p style="margin:0 0 26px;">
                                  <a href="%s" style="display:inline-block;background:#6d5dfc;color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;border-radius:12px;padding:13px 18px;">%s</a>
                                </p>
                                <p style="margin:0 0 10px;color:#667085;font-size:13px;line-height:1.6;">%s</p>
                                <p style="margin:0 0 24px;color:#667085;font-size:13px;line-height:1.6;">If the button does not work, copy and paste this URL into your browser:</p>
                                <p style="margin:0 0 24px;word-break:break-all;color:#374151;font-size:13px;line-height:1.6;"><a href="%s" style="color:#6d5dfc;">%s</a></p>
                              </td>
                            </tr>
                            <tr>
                              <td style="border-top:1px solid #eef0f4;padding:18px 32px 30px;">
                                <p style="margin:0;color:#667085;font-size:13px;line-height:1.6;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """
                .formatted(
                        safeHeading,
                        safeFirstParagraph,
                        secondaryParagraph,
                        safeActionUrl,
                        safeActionLabel,
                        safeExpiration,
                        safeActionUrl,
                        safeActionUrl,
                        safeIgnoreCopy);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
