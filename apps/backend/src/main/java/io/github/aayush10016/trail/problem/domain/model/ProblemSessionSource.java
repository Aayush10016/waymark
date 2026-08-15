package io.github.aayush10016.trail.problem.domain.model;

/**
 * Source that created a Problem Session.
 */
public enum ProblemSessionSource {
    /**
     * User explicitly started the session.
     */
    MANUAL,

    /**
     * Deterministic detection created the candidate.
     */
    DETECTED
}
