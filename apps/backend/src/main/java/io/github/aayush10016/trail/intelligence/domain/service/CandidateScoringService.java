package io.github.aayush10016.trail.intelligence.domain.service;

import io.github.aayush10016.trail.intelligence.domain.model.CandidateBoundaryContext;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateDecisionStatus;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateDetectionDecision;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateReasonCode;
import io.github.aayush10016.trail.intelligence.domain.model.CandidateSuppressionReasonCode;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionFeatureSet;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionReasonCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic candidate scoring, boundary, and suppression policy.
 *
 * <p>This service consumes ENG-042 features. It does not inspect raw events, call models,
 * persist sessions, or perform lifecycle transitions.
 */
public final class CandidateScoringService {

    /**
     * Stable scoring policy version for generated decisions.
     */
    public static final String ALGORITHM_VERSION = "candidate-scoring-v1";

    /**
     * Conservative threshold required for automatic candidate decisions.
     */
    public static final int CANDIDATE_THRESHOLD = 7;

    /**
     * Maximum score emitted by this deterministic policy.
     */
    public static final int MAXIMUM_SCORE = 12;

    /**
     * Maximum coherent automatic candidate window before topic-gap suppression.
     */
    public static final Duration MAXIMUM_CANDIDATE_WINDOW = Duration.ofMinutes(30);

    private static final int SEARCH_QUERY_WEIGHT = 3;
    private static final int ERROR_SIGNAL_WEIGHT = 4;
    private static final int MULTIPLE_DOMAIN_WEIGHT = 2;
    private static final int DOMAIN_TRANSITION_WEIGHT = 2;
    private static final int REPEATED_DOMAIN_WEIGHT = 1;

    /**
     * Scores a feature set and applies deterministic boundary/suppression rules.
     *
     * @param features bounded features from ENG-042
     * @param context explicit boundary/suppression context
     * @return immutable candidate decision
     */
    public CandidateDetectionDecision evaluate(DetectionFeatureSet features, CandidateBoundaryContext context) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(context, "context");

        if (context.manualSessionActive()) {
            return candidate(MAXIMUM_SCORE, List.of(CandidateReasonCode.MANUAL_PRECEDENCE));
        }
        if (features.eventCount() == 0) {
            return suppressed(0, List.of(), CandidateSuppressionReasonCode.EMPTY_FEATURES);
        }
        if (!context.inactivityBoundaryReached() && !context.topicShiftBoundaryReached()) {
            return suppressed(
                    score(features),
                    scoreReasonCodes(features),
                    CandidateSuppressionReasonCode.ACTIVE_NAVIGATION_BOUNDARY_NOT_REACHED);
        }
        if (features.windowDuration().compareTo(MAXIMUM_CANDIDATE_WINDOW) > 0) {
            return suppressed(score(features), scoreReasonCodes(features), CandidateSuppressionReasonCode.TOPIC_GAP_TOO_LARGE);
        }
        if (context.dismissalSuppressionActive()) {
            return suppressed(
                    score(features),
                    boundaryAndScoreReasonCodes(features, context),
                    CandidateSuppressionReasonCode.DISMISSED_SUPPRESSION_ACTIVE);
        }

        var score = score(features);
        var reasons = boundaryAndScoreReasonCodes(features, context);
        if (score >= CANDIDATE_THRESHOLD) {
            return candidate(score, reasons);
        }
        return suppressed(score, reasons, CandidateSuppressionReasonCode.INSUFFICIENT_EVIDENCE);
    }

    private CandidateDetectionDecision candidate(int score, List<CandidateReasonCode> reasonCodes) {
        return new CandidateDetectionDecision(
                CandidateDecisionStatus.CANDIDATE,
                Math.min(score, MAXIMUM_SCORE),
                CANDIDATE_THRESHOLD,
                ALGORITHM_VERSION,
                reasonCodes,
                null);
    }

    private CandidateDetectionDecision suppressed(
            int score, List<CandidateReasonCode> reasonCodes, CandidateSuppressionReasonCode suppressionReason) {
        return new CandidateDetectionDecision(
                CandidateDecisionStatus.SUPPRESSED,
                Math.min(score, MAXIMUM_SCORE),
                CANDIDATE_THRESHOLD,
                ALGORITHM_VERSION,
                reasonCodes,
                suppressionReason);
    }

    private static int score(DetectionFeatureSet features) {
        var score = 0;
        if (hasReason(features, DetectionReasonCode.SEARCH_QUERY_PRESENT)) {
            score += SEARCH_QUERY_WEIGHT;
        }
        if (hasReason(features, DetectionReasonCode.ERROR_PHRASE_PRESENT)) {
            score += ERROR_SIGNAL_WEIGHT;
        }
        if (hasReason(features, DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT)) {
            score += MULTIPLE_DOMAIN_WEIGHT;
        }
        if (hasReason(features, DetectionReasonCode.DOMAIN_TRANSITION_PRESENT)) {
            score += DOMAIN_TRANSITION_WEIGHT;
        }
        if (hasReason(features, DetectionReasonCode.REPEATED_DOMAIN_PRESENT)) {
            score += REPEATED_DOMAIN_WEIGHT;
        }
        return Math.min(score, MAXIMUM_SCORE);
    }

    private static List<CandidateReasonCode> boundaryAndScoreReasonCodes(
            DetectionFeatureSet features, CandidateBoundaryContext context) {
        var reasons = new ArrayList<CandidateReasonCode>();
        if (context.inactivityBoundaryReached()) {
            reasons.add(CandidateReasonCode.INACTIVITY_BOUNDARY);
        }
        if (context.topicShiftBoundaryReached()) {
            reasons.add(CandidateReasonCode.TOPIC_SHIFT_BOUNDARY);
        }
        reasons.addAll(scoreReasonCodes(features));
        return reasons;
    }

    private static List<CandidateReasonCode> scoreReasonCodes(DetectionFeatureSet features) {
        var reasons = new ArrayList<CandidateReasonCode>();
        if (hasReason(features, DetectionReasonCode.SEARCH_QUERY_PRESENT)) {
            reasons.add(CandidateReasonCode.SEARCH_QUERY_SIGNAL);
        }
        if (hasReason(features, DetectionReasonCode.ERROR_PHRASE_PRESENT)) {
            reasons.add(CandidateReasonCode.ERROR_SIGNAL);
        }
        if (hasReason(features, DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT)) {
            reasons.add(CandidateReasonCode.MULTIPLE_DOMAIN_SIGNAL);
        }
        if (hasReason(features, DetectionReasonCode.DOMAIN_TRANSITION_PRESENT)) {
            reasons.add(CandidateReasonCode.DOMAIN_TRANSITION_SIGNAL);
        }
        if (hasReason(features, DetectionReasonCode.REPEATED_DOMAIN_PRESENT)) {
            reasons.add(CandidateReasonCode.REPEATED_DOMAIN_SIGNAL);
        }
        return reasons;
    }

    private static boolean hasReason(DetectionFeatureSet features, DetectionReasonCode reasonCode) {
        return features.reasonCodes().contains(reasonCode);
    }
}
