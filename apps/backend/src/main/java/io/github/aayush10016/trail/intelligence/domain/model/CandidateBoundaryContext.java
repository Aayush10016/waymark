package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Instant;

/**
 * Explicit boundary and suppression context for deterministic candidate scoring.
 *
 * @param manualSessionActive whether a user-started Problem Session boundary is active
 * @param inactivityBoundaryReached whether active browsing has paused enough to evaluate safely
 * @param topicShiftBoundaryReached whether the current evidence window closed due to topic shift
 * @param evaluatedAt explicit evaluation time; required when dismissal suppression is supplied
 * @param dismissedUntil optional suppression expiry for previously dismissed automatic signals
 */
public record CandidateBoundaryContext(
        boolean manualSessionActive,
        boolean inactivityBoundaryReached,
        boolean topicShiftBoundaryReached,
        Instant evaluatedAt,
        Instant dismissedUntil) {

    /**
     * Returns a context with no manual, inactivity, topic-shift, or dismissal boundary.
     */
    public static CandidateBoundaryContext activeNavigation() {
        return new CandidateBoundaryContext(false, false, false, null, null);
    }

    /**
     * Returns a context where inactivity permits automatic candidate evaluation.
     */
    public static CandidateBoundaryContext inactive() {
        return new CandidateBoundaryContext(false, true, false, null, null);
    }

    /**
     * Returns a context where topic shift permits automatic candidate evaluation.
     */
    public static CandidateBoundaryContext topicShift() {
        return new CandidateBoundaryContext(false, false, true, null, null);
    }

    /**
     * Returns a context where manual session state takes precedence.
     */
    public static CandidateBoundaryContext manualSession() {
        return new CandidateBoundaryContext(true, false, false, null, null);
    }

    /**
     * Returns whether a dismissal suppression is currently active.
     */
    public boolean dismissalSuppressionActive() {
        return evaluatedAt != null && dismissedUntil != null && evaluatedAt.isBefore(dismissedUntil);
    }
}
