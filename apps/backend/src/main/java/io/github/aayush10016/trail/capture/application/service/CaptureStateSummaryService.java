package io.github.aayush10016.trail.capture.application.service;

import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse;
import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse.CapturePolicyFreshnessResponse;
import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse.CurrentCandidateResponse;
import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse.PromptEligibilityResponse;
import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse.RecommendationSummaryResponse;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureStateRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for backend capture-state summaries.
 */
public class CaptureStateSummaryService {

    /**
     * Required server-side browser metadata consent policy for v1.1.
     */
    public static final String REQUIRED_CAPTURE_POLICY_VERSION = "capture-consent-v1";

    private final CaptureConsentRepository consentRepository;
    private final CaptureStateRepository captureStateRepository;
    private final FeatureFlagService featureFlagService;

    /**
     * Creates the capture-state summary service.
     */
    public CaptureStateSummaryService(
            CaptureConsentRepository consentRepository,
            CaptureStateRepository captureStateRepository,
            FeatureFlagService featureFlagService) {
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository");
        this.captureStateRepository = Objects.requireNonNull(captureStateRepository, "captureStateRepository");
        this.featureFlagService = Objects.requireNonNull(featureFlagService, "featureFlagService");
    }

    /**
     * Reads the authenticated user's bounded backend capture-state summary.
     */
    @Transactional(readOnly = true)
    public CaptureStateSummaryResponse read(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        var hasConsent = consentRepository.hasActiveCaptureConsent(principal);
        var observedPolicy = consentRepository.latestCapturePolicyVersion(principal).orElse(null);
        var currentCandidate = captureStateRepository.findLatestCurrentCandidate(principal.userId());
        var promptEligibility = promptEligibility(hasConsent, currentCandidate.isPresent());
        var candidateResponse = currentCandidate.map(CurrentCandidateResponse::from).orElse(null);
        var recommendationSummary = new RecommendationSummaryResponse(
                currentCandidate.map(candidate -> candidate.problemSessionId()).orElse(null),
                recommendationStatus(currentCandidate.isPresent()));

        return new CaptureStateSummaryResponse(
                candidateResponse,
                promptEligibility,
                recommendationSummary,
                new CapturePolicyFreshnessResponse(
                        REQUIRED_CAPTURE_POLICY_VERSION,
                        observedPolicy,
                        REQUIRED_CAPTURE_POLICY_VERSION.equals(observedPolicy)));
    }

    private static PromptEligibilityResponse promptEligibility(boolean hasConsent, boolean hasCandidate) {
        if (!hasConsent) {
            return new PromptEligibilityResponse("INELIGIBLE", "CONSENT_REQUIRED");
        }
        if (!hasCandidate) {
            return new PromptEligibilityResponse("INELIGIBLE", "NO_CURRENT_CANDIDATE");
        }
        return new PromptEligibilityResponse("ELIGIBLE", "CURRENT_CANDIDATE_READY");
    }

    private String recommendationStatus(boolean hasCandidate) {
        if (!hasCandidate) {
            return "NO_CURRENT_CANDIDATE";
        }
        if (!recommendationsEnabled()) {
            return "RECOMMENDATION_DISABLED";
        }
        return "DETAIL_ENDPOINT_REQUIRED";
    }

    private boolean recommendationsEnabled() {
        return featureFlagService.isEnabled(FeatureFlag.AI_SUGGESTIONS)
                && featureFlagService.isEnabled(FeatureFlag.GHOST_REPLAY)
                && featureFlagService.isEnabled(FeatureFlag.SEMANTIC_SEARCH);
    }
}
