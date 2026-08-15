package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Deterministic candidate detection outcome.
 */
public enum CandidateDecisionStatus {
    /**
     * The bounded evidence is sufficient to progress as a candidate decision.
     */
    CANDIDATE,

    /**
     * The bounded evidence must not progress as a candidate.
     */
    SUPPRESSED
}
