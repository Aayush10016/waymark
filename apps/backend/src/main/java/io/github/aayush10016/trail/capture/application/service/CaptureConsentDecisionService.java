package io.github.aayush10016.trail.capture.application.service;

import io.github.aayush10016.trail.capture.application.dto.CaptureConsentDecisionResponse;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Application service for recording explicit capture consent decisions.
 */
public class CaptureConsentDecisionService {

    /**
     * Capture consent policy version accepted by Trail v1.1.
     */
    public static final String REQUIRED_CAPTURE_POLICY_VERSION = "capture-consent-v1";

    private final Clock clock;
    private final CaptureConsentRepository consentRepository;

    /**
     * Creates the service.
     */
    public CaptureConsentDecisionService(CaptureConsentRepository consentRepository, Clock clock) {
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records explicit consent for browser navigation metadata capture.
     */
    public CaptureConsentDecisionResponse grant(
            AuthenticatedPrincipal principal, String policyVersion, Instant decidedAt) {
        var validatedPolicyVersion = validatePolicyVersion(policyVersion);
        var validatedDecisionTime = validateDecisionTime(decidedAt);
        consentRepository.recordGrantedCaptureConsent(principal, validatedPolicyVersion, validatedDecisionTime);
        return new CaptureConsentDecisionResponse("GRANTED", validatedPolicyVersion, validatedDecisionTime);
    }

    /**
     * Records withdrawal of browser navigation metadata capture consent.
     */
    public CaptureConsentDecisionResponse withdraw(
            AuthenticatedPrincipal principal, String policyVersion, Instant decidedAt) {
        var validatedPolicyVersion = validatePolicyVersion(policyVersion);
        var validatedDecisionTime = validateDecisionTime(decidedAt);
        consentRepository.recordWithdrawnCaptureConsent(principal, validatedPolicyVersion, validatedDecisionTime);
        return new CaptureConsentDecisionResponse("WITHDRAWN", validatedPolicyVersion, validatedDecisionTime);
    }

    private static String validatePolicyVersion(String policyVersion) {
        if (policyVersion == null || !REQUIRED_CAPTURE_POLICY_VERSION.equals(policyVersion.trim())) {
            throw new InvalidCaptureConsentDecisionException("Capture consent policy version is unsupported.");
        }
        return REQUIRED_CAPTURE_POLICY_VERSION;
    }

    private Instant validateDecisionTime(Instant decidedAt) {
        if (decidedAt == null) {
            return clock.instant();
        }
        return decidedAt;
    }
}
