package io.github.aayush10016.trail.identity.application.dto;

/**
 * Generic email verification result that does not reveal account or token
 * state.
 *
 * @param status client-visible status
 */
public record EmailVerificationResult(EmailVerificationStatus status) {

    private static final EmailVerificationResult ACCEPTED = new EmailVerificationResult(EmailVerificationStatus.ACCEPTED);

    /**
     * Returns the generic accepted result.
     *
     * @return accepted result
     */
    public static EmailVerificationResult accepted() {
        return ACCEPTED;
    }
}
