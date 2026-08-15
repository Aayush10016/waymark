package io.github.aayush10016.trail.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.capture.domain.model.CurrentCaptureCandidate;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureStateRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaptureStateSummaryServiceFeatureFlagTest {

    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("76100000-0000-4000-8000-000000000001"));
    private static final UUID SESSION_ID = UUID.fromString("76100000-0000-4000-8000-000000000002");

    @Test
    void reportsRecommendationDisabledWhenAnyRequiredRecommendationGateIsDisabled() {
        var service = new CaptureStateSummaryService(
                activeConsentRepository(),
                currentCandidateRepository(),
                featureFlag -> false);

        var summary = service.read(PRINCIPAL);

        assertThat(summary.currentCandidate()).isNotNull();
        assertThat(summary.recommendation().currentProblemSessionId()).isEqualTo(SESSION_ID);
        assertThat(summary.recommendation().status()).isEqualTo("RECOMMENDATION_DISABLED");
        assertThat(summary.policyFreshness().fresh()).isTrue();
    }

    private static CaptureConsentRepository activeConsentRepository() {
        return new CaptureConsentRepository() {
            @Override
            public boolean hasActiveCaptureConsent(AuthenticatedPrincipal principal) {
                return PRINCIPAL.equals(principal);
            }

            @Override
            public Optional<String> latestCapturePolicyVersion(AuthenticatedPrincipal principal) {
                return Optional.of(CaptureStateSummaryService.REQUIRED_CAPTURE_POLICY_VERSION);
            }

            @Override
            public void recordGrantedCaptureConsent(
                    AuthenticatedPrincipal principal, String policyVersion, Instant grantedAt) {
            }

            @Override
            public void recordWithdrawnCaptureConsent(
                    AuthenticatedPrincipal principal, String policyVersion, Instant withdrawnAt) {
            }
        };
    }

    private static CaptureStateRepository currentCandidateRepository() {
        return userId -> Optional.of(new CurrentCaptureCandidate(
                SESSION_ID,
                "CANDIDATE",
                "Spring Security CORS",
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T08:05:00Z")));
    }
}
