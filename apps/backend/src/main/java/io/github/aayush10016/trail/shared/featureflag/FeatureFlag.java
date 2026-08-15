package io.github.aayush10016.trail.shared.featureflag;

import java.time.LocalDate;

/**
 * Global backend feature flags approved for Trail v1.1.
 */
public enum FeatureFlag {
    AI_SUGGESTIONS(
            "ai-suggestions",
            "Technical Lead",
            "Gate AI-generated suggestion behavior until provider quality and cost are validated.",
            false,
            LocalDate.of(2026, 7, 5),
            "Remove after AI suggestion behavior is stable or retired."),
    GHOST_REPLAY(
            "ghost-replay",
            "Product Engineering Lead",
            "Gate Ghost Replay behavior until recommendation evidence and UX are validated.",
            false,
            LocalDate.of(2026, 7, 5),
            "Remove after Ghost Replay is stable or retired."),
    SEMANTIC_SEARCH(
            "semantic-search",
            "Retrieval Lead",
            "Gate semantic search behavior until embedding recall quality is validated.",
            false,
            LocalDate.of(2026, 7, 5),
            "Remove after semantic search is stable or retired."),
    EXPERIMENTAL_FEATURES(
            "experimental-features",
            "Technical Lead",
            "Gate explicitly experimental backend behavior that is not yet part of the stable MVP path.",
            false,
            LocalDate.of(2026, 7, 5),
            "Remove when no approved experimental backend behavior remains.");

    private final String key;
    private final String owner;
    private final String purpose;
    private final boolean defaultEnabled;
    private final LocalDate createdOn;
    private final String removalCondition;

    FeatureFlag(
            String key,
            String owner,
            String purpose,
            boolean defaultEnabled,
            LocalDate createdOn,
            String removalCondition) {
        this.key = key;
        this.owner = owner;
        this.purpose = purpose;
        this.defaultEnabled = defaultEnabled;
        this.createdOn = createdOn;
        this.removalCondition = removalCondition;
    }

    public String key() {
        return key;
    }

    public String owner() {
        return owner;
    }

    public String purpose() {
        return purpose;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public LocalDate createdOn() {
        return createdOn;
    }

    public String removalCondition() {
        return removalCondition;
    }
}
