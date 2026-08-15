package io.github.aayush10016.trail.identity.domain.model;

import java.util.Objects;

/**
 * Immutable outbound email message for identity security actions.
 */
public final class EmailDeliveryMessage {

    private static final int MAXIMUM_SUBJECT_LENGTH = 120;
    private static final int MAXIMUM_TEXT_BODY_LENGTH = 6_000;
    private static final int MAXIMUM_HTML_BODY_LENGTH = 16_000;

    private final EmailAddress recipient;
    private final EmailDeliveryTemplate template;
    private final String subject;
    private final String textBody;
    private final String htmlBody;

    /**
     * Creates an outbound identity email message.
     *
     * @param recipient message recipient
     * @param template message template identity
     * @param subject email subject
     * @param textBody plain-text body
     */
    public EmailDeliveryMessage(
            EmailAddress recipient, EmailDeliveryTemplate template, String subject, String textBody) {
        this(recipient, template, subject, textBody, "");
    }

    /**
     * Creates an outbound identity email message with plain-text and HTML bodies.
     *
     * @param recipient message recipient
     * @param template message template identity
     * @param subject email subject
     * @param textBody plain-text body
     * @param htmlBody HTML body, or blank when the provider should send text only
     */
    public EmailDeliveryMessage(
            EmailAddress recipient,
            EmailDeliveryTemplate template,
            String subject,
            String textBody,
            String htmlBody) {
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.template = Objects.requireNonNull(template, "template");
        this.subject = validateText("subject", subject, MAXIMUM_SUBJECT_LENGTH);
        this.textBody = validateText("textBody", textBody, MAXIMUM_TEXT_BODY_LENGTH);
        this.htmlBody = validateOptionalText("htmlBody", htmlBody, MAXIMUM_HTML_BODY_LENGTH);
    }

    /**
     * Returns the recipient.
     *
     * @return recipient email address
     */
    public EmailAddress recipient() {
        return recipient;
    }

    /**
     * Returns the template identity.
     *
     * @return template identity
     */
    public EmailDeliveryTemplate template() {
        return template;
    }

    /**
     * Returns the email subject.
     *
     * @return subject
     */
    public String subject() {
        return subject;
    }

    /**
     * Returns the plain-text body.
     *
     * @return plain-text body
     */
    public String textBody() {
        return textBody;
    }

    /**
     * Returns the HTML body.
     *
     * @return HTML body, or an empty string when no HTML body is present
     */
    public String htmlBody() {
        return htmlBody;
    }

    /**
     * Returns whether this message contains an HTML body.
     *
     * @return true when HTML content should be delivered
     */
    public boolean hasHtmlBody() {
        return !htmlBody.isBlank();
    }

    @Override
    public String toString() {
        return "EmailDeliveryMessage[template=" + template + "]";
    }

    private static String validateText(String fieldName, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var trimmed = value.strip();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return trimmed;
    }

    private static String validateOptionalText(String fieldName, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var trimmed = value.strip();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return trimmed;
    }
}
