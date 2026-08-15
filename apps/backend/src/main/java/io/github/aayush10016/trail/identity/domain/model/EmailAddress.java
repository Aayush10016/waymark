package io.github.aayush10016.trail.identity.domain.model;

import java.util.Locale;

/**
 * User email value with preserved display form and normalized login key.
 *
 * @param display preserved, trimmed user-provided email form
 * @param normalized lower-case normalized email used for uniqueness
 */
public record EmailAddress(String display, String normalized) {

    private static final int MINIMUM_LENGTH = 3;
    private static final int MAXIMUM_LENGTH = 320;

    /**
     * Creates a validated email value.
     */
    public EmailAddress {
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("display email must not be blank");
        }
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("normalized email must not be blank");
        }
        if (!normalized.equals(normalized.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("normalized email must be lower-case");
        }
        if (!normalized.equals(normalized.strip())) {
            throw new IllegalArgumentException("normalized email must be trimmed");
        }
        validateShape(normalized);
    }

    /**
     * Normalizes a raw email string while preserving the trimmed display form.
     *
     * @param rawEmail user-provided email
     * @return normalized email value
     */
    public static EmailAddress from(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        var display = rawEmail.strip();
        var normalized = display.toLowerCase(Locale.ROOT);
        return new EmailAddress(display, normalized);
    }

    private static void validateShape(String normalized) {
        if (normalized.length() < MINIMUM_LENGTH || normalized.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("email length is invalid");
        }

        var atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex != normalized.lastIndexOf('@') || atIndex == normalized.length() - 1) {
            throw new IllegalArgumentException("email shape is invalid");
        }
    }
}
