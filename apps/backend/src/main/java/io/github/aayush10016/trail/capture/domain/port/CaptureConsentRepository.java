package io.github.aayush10016.trail.capture.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads and records server-side capture consent evidence.
 */
public interface CaptureConsentRepository {

    /**
     * Checks whether the user currently has active server-side browser metadata capture consent.
     *
     * @param principal authenticated user
     * @return true when latest consent evidence grants capture
     */
    boolean hasActiveCaptureConsent(AuthenticatedPrincipal principal);

    /**
     * Returns the latest capture consent policy version seen for the user, when available.
     */
    default Optional<String> latestCapturePolicyVersion(AuthenticatedPrincipal principal) {
        return Optional.empty();
    }

    /**
     * Records explicit browser navigation metadata capture consent.
     */
    void recordGrantedCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant grantedAt);

    /**
     * Records withdrawal of browser navigation metadata capture consent.
     */
    void recordWithdrawnCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant withdrawnAt);
}
