package io.github.aayush10016.trail.identity.application.dto;

/**
 * Generic registration result safe to expose for both newly created and
 * already-registered emails.
 *
 * @param status client-visible status
 */
public record RegistrationResult(RegistrationStatus status) {

    private static final RegistrationResult ACCEPTED = new RegistrationResult(RegistrationStatus.ACCEPTED);

    /**
     * Returns the generic accepted result.
     *
     * @return accepted registration result
     */
    public static RegistrationResult accepted() {
        return ACCEPTED;
    }
}
