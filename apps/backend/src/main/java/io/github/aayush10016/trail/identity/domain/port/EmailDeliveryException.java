package io.github.aayush10016.trail.identity.domain.port;

/**
 * Typed email delivery failure with explicit retry semantics.
 */
public final class EmailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EmailDeliveryFailureType failureType;

    private EmailDeliveryException(EmailDeliveryFailureType failureType, String message, Throwable cause) {
        super(validateMessage(message), cause);
        this.failureType = failureType;
    }

    /**
     * Creates a retryable email delivery failure.
     *
     * @param message content-safe failure message
     * @param cause underlying cause
     * @return retryable delivery failure
     */
    public static EmailDeliveryException retryable(String message, Throwable cause) {
        return new EmailDeliveryException(EmailDeliveryFailureType.RETRYABLE, message, cause);
    }

    /**
     * Creates a permanent email delivery failure.
     *
     * @param message content-safe failure message
     * @param cause underlying cause
     * @return permanent delivery failure
     */
    public static EmailDeliveryException permanent(String message, Throwable cause) {
        return new EmailDeliveryException(EmailDeliveryFailureType.PERMANENT, message, cause);
    }

    /**
     * Returns the delivery failure type.
     *
     * @return failure type
     */
    public EmailDeliveryFailureType failureType() {
        return failureType;
    }

    /**
     * Returns whether this failure can be retried safely.
     *
     * @return true when retryable
     */
    public boolean retryable() {
        return failureType == EmailDeliveryFailureType.RETRYABLE;
    }

    private static String validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message.strip();
    }
}
