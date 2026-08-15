package io.github.aayush10016.trail.intelligence.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.intelligence.domain.model.CandidateBoundaryContext;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateDecisionStatus;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateReasonCode;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateSuppressionReasonCode;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionFeatureSet;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionReasonCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateScoringServiceTest {

    private final CandidateScoringService service = new CandidateScoringService();

    @Test
    void producesCandidateForGoldenDebuggingFixtureAtThreshold() {
        var decision = service.evaluate(thresholdFeatures(), CandidateBoundaryContext.inactive());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
        assertThat(decision.isCandidate()).isTrue();
        assertThat(decision.score()).isEqualTo(CandidateScoringService.CANDIDATE_THRESHOLD);
        assertThat(decision.threshold()).isEqualTo(CandidateScoringService.CANDIDATE_THRESHOLD);
        assertThat(decision.algorithmVersion()).isEqualTo(CandidateScoringService.ALGORITHM_VERSION);
        assertThat(decision.reasonCodes())
                .containsExactly(
                        CandidateReasonCode.INACTIVITY_BOUNDARY,
                        CandidateReasonCode.SEARCH_QUERY_SIGNAL,
                        CandidateReasonCode.ERROR_SIGNAL);
        assertThat(decision.suppressionReason()).isNull();
    }

    @Test
    void identicalFeaturesAndContextProduceIdenticalDecision() {
        var features = richDebuggingFeatures();
        var context = CandidateBoundaryContext.topicShift();

        assertThat(service.evaluate(features, context)).isEqualTo(service.evaluate(features, context));
    }

    @Test
    void suppressesBelowThresholdEvidence() {
        var decision = service.evaluate(lowSignalFeatures(), CandidateBoundaryContext.inactive());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.SUPPRESSED);
        assertThat(decision.score()).isEqualTo(3);
        assertThat(decision.suppressionReason()).isEqualTo(CandidateSuppressionReasonCode.INSUFFICIENT_EVIDENCE);
        assertThat(decision.reasonCodes())
                .containsExactly(CandidateReasonCode.INACTIVITY_BOUNDARY, CandidateReasonCode.SEARCH_QUERY_SIGNAL);
    }

    @Test
    void manualPrecedenceCreatesCandidateWithoutAutomaticEvidence() {
        var decision = service.evaluate(emptyFeatures(), CandidateBoundaryContext.manualSession());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
        assertThat(decision.score()).isEqualTo(CandidateScoringService.MAXIMUM_SCORE);
        assertThat(decision.reasonCodes()).containsExactly(CandidateReasonCode.MANUAL_PRECEDENCE);
    }

    @Test
    void manualPrecedenceOverridesDismissalSuppression() {
        var context = new CandidateBoundaryContext(
                true,
                false,
                false,
                Instant.parse("2026-07-13T08:15:00Z"),
                Instant.parse("2026-07-13T08:45:00Z"));

        var decision = service.evaluate(emptyFeatures(), context);

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
        assertThat(decision.reasonCodes()).containsExactly(CandidateReasonCode.MANUAL_PRECEDENCE);
    }

    @Test
    void suppressesAutomaticCandidateDuringActiveNavigationBeforeBoundary() {
        var decision = service.evaluate(richDebuggingFeatures(), CandidateBoundaryContext.activeNavigation());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.SUPPRESSED);
        assertThat(decision.score()).isEqualTo(CandidateScoringService.MAXIMUM_SCORE);
        assertThat(decision.suppressionReason())
                .isEqualTo(CandidateSuppressionReasonCode.ACTIVE_NAVIGATION_BOUNDARY_NOT_REACHED);
        assertThat(decision.reasonCodes())
                .containsExactly(
                        CandidateReasonCode.SEARCH_QUERY_SIGNAL,
                        CandidateReasonCode.ERROR_SIGNAL,
                        CandidateReasonCode.MULTIPLE_DOMAIN_SIGNAL,
                        CandidateReasonCode.DOMAIN_TRANSITION_SIGNAL,
                        CandidateReasonCode.REPEATED_DOMAIN_SIGNAL);
    }

    @Test
    void topicShiftBoundaryAllowsCandidateEvaluation() {
        var decision = service.evaluate(thresholdFeatures(), CandidateBoundaryContext.topicShift());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
        assertThat(decision.reasonCodes()).startsWith(CandidateReasonCode.TOPIC_SHIFT_BOUNDARY);
    }

    @Test
    void suppressesLongTopicGapWindows() {
        var decision = service.evaluate(longTopicGapFeatures(), CandidateBoundaryContext.inactive());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.SUPPRESSED);
        assertThat(decision.suppressionReason()).isEqualTo(CandidateSuppressionReasonCode.TOPIC_GAP_TOO_LARGE);
    }

    @Test
    void suppressesRecentlyDismissedAutomaticSignals() {
        var context = new CandidateBoundaryContext(
                false,
                true,
                false,
                Instant.parse("2026-07-13T08:15:00Z"),
                Instant.parse("2026-07-13T08:45:00Z"));

        var decision = service.evaluate(richDebuggingFeatures(), context);

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.SUPPRESSED);
        assertThat(decision.suppressionReason()).isEqualTo(CandidateSuppressionReasonCode.DISMISSED_SUPPRESSION_ACTIVE);
    }

    @Test
    void dismissalSuppressionExpiresDeterministically() {
        var context = new CandidateBoundaryContext(
                false,
                true,
                false,
                Instant.parse("2026-07-13T08:45:00Z"),
                Instant.parse("2026-07-13T08:45:00Z"));

        var decision = service.evaluate(thresholdFeatures(), context);

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
    }

    @Test
    void suppressesEmptyFeatures() {
        var decision = service.evaluate(emptyFeatures(), CandidateBoundaryContext.inactive());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.SUPPRESSED);
        assertThat(decision.score()).isZero();
        assertThat(decision.suppressionReason()).isEqualTo(CandidateSuppressionReasonCode.EMPTY_FEATURES);
        assertThat(decision.reasonCodes()).isEmpty();
    }

    @Test
    void boundsExtremeScore() {
        var decision = service.evaluate(richDebuggingFeatures(), CandidateBoundaryContext.inactive());

        assertThat(decision.status()).isEqualTo(CandidateDecisionStatus.CANDIDATE);
        assertThat(decision.score()).isEqualTo(CandidateScoringService.MAXIMUM_SCORE);
    }

    @Test
    void decisionsAreImmutableAndDoNotMutateInputFeatures() {
        var features = richDebuggingFeatures();

        var decision = service.evaluate(features, CandidateBoundaryContext.inactive());

        assertThat(features.reasonCodes())
                .containsExactly(
                        DetectionReasonCode.TIME_WINDOW_PRESENT,
                        DetectionReasonCode.SEARCH_QUERY_PRESENT,
                        DetectionReasonCode.ERROR_PHRASE_PRESENT,
                        DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT,
                        DetectionReasonCode.DOMAIN_TRANSITION_PRESENT,
                        DetectionReasonCode.REPEATED_DOMAIN_PRESENT);
        assertThatThrownBy(() -> decision.reasonCodes().add(CandidateReasonCode.MANUAL_PRECEDENCE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullInputsPredictably() {
        assertThatThrownBy(() -> service.evaluate(null, CandidateBoundaryContext.inactive()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.evaluate(emptyFeatures(), null)).isInstanceOf(NullPointerException.class);
    }

    private static DetectionFeatureSet emptyFeatures() {
        return new DetectionFeatureSet(
                0,
                Duration.ZERO,
                0,
                List.of(),
                0,
                List.of(),
                0,
                0,
                List.of(),
                List.of(DetectionReasonCode.NO_EVENTS));
    }

    private static DetectionFeatureSet lowSignalFeatures() {
        return new DetectionFeatureSet(
                1,
                Duration.ZERO,
                1,
                List.of("spring security guide"),
                1,
                List.of("docs.spring.io"),
                0,
                0,
                List.of(),
                List.of(DetectionReasonCode.TIME_WINDOW_PRESENT, DetectionReasonCode.SEARCH_QUERY_PRESENT));
    }

    private static DetectionFeatureSet thresholdFeatures() {
        return new DetectionFeatureSet(
                2,
                Duration.ofMinutes(6),
                1,
                List.of("spring security cors not working"),
                1,
                List.of("docs.spring.io"),
                0,
                0,
                List.of("cors", "not working"),
                List.of(
                        DetectionReasonCode.TIME_WINDOW_PRESENT,
                        DetectionReasonCode.SEARCH_QUERY_PRESENT,
                        DetectionReasonCode.ERROR_PHRASE_PRESENT));
    }

    private static DetectionFeatureSet longTopicGapFeatures() {
        return new DetectionFeatureSet(
                4,
                CandidateScoringService.MAXIMUM_CANDIDATE_WINDOW.plusMinutes(1),
                1,
                List.of("spring security cors not working"),
                3,
                List.of("www.google.com", "docs.spring.io", "stackoverflow.com"),
                3,
                1,
                List.of("cors", "not working"),
                List.of(
                        DetectionReasonCode.TIME_WINDOW_PRESENT,
                        DetectionReasonCode.SEARCH_QUERY_PRESENT,
                        DetectionReasonCode.ERROR_PHRASE_PRESENT,
                        DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT,
                        DetectionReasonCode.DOMAIN_TRANSITION_PRESENT,
                        DetectionReasonCode.REPEATED_DOMAIN_PRESENT));
    }

    private static DetectionFeatureSet richDebuggingFeatures() {
        return new DetectionFeatureSet(
                4,
                Duration.ofMinutes(12),
                1,
                List.of("spring security cors not working"),
                3,
                List.of("www.google.com", "docs.spring.io", "stackoverflow.com"),
                3,
                1,
                List.of("cors", "not working", "error"),
                List.of(
                        DetectionReasonCode.TIME_WINDOW_PRESENT,
                        DetectionReasonCode.SEARCH_QUERY_PRESENT,
                        DetectionReasonCode.ERROR_PHRASE_PRESENT,
                        DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT,
                        DetectionReasonCode.DOMAIN_TRANSITION_PRESENT,
                        DetectionReasonCode.REPEATED_DOMAIN_PRESENT));
    }
}
