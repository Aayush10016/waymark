package io.github.aayush10016.trail.retrieval.domain.model;

/**
 * Allowlisted recommendation lifecycle event outcomes.
 */
public enum RecommendationEventOutcome {
    /**
     * The server showed a recommendation to the user.
     */
    SHOWN,

    /**
     * The user opened the recommended solution or resource.
     */
    OPENED,

    /**
     * The user marked the recommendation as helpful.
     */
    HELPFUL,

    /**
     * The user marked the recommendation as unrelated.
     */
    NOT_RELATED,

    /**
     * The user dismissed the recommendation.
     */
    DISMISSED;

    /**
     * Returns true when clients may submit this event outcome directly.
     */
    public boolean clientSubmittable() {
        return this != SHOWN;
    }
}
