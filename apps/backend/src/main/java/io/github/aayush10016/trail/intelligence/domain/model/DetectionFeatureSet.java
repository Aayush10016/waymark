package io.github.aayush10016.trail.intelligence.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic bounded features extracted from sanitized capture metadata.
 *
 * @param eventCount number of events considered after bounding
 * @param windowDuration bounded event time span
 * @param searchQueryCount number of considered events with an allowlisted search query
 * @param searchQueries bounded normalized search queries
 * @param uniqueDomainCount number of unique domains before domain-list truncation
 * @param domains bounded unique domains in chronological order
 * @param domainTransitionCount bounded count of chronological domain changes
 * @param repeatedDomainCount bounded count of domains visited more than once
 * @param errorPhrases bounded normalized technical error phrases
 * @param reasonCodes deterministic evidence codes explaining extracted signals
 */
public record DetectionFeatureSet(
        int eventCount,
        Duration windowDuration,
        int searchQueryCount,
        List<String> searchQueries,
        int uniqueDomainCount,
        List<String> domains,
        int domainTransitionCount,
        int repeatedDomainCount,
        List<String> errorPhrases,
        List<DetectionReasonCode> reasonCodes) {

    /**
     * Creates an immutable feature set.
     */
    public DetectionFeatureSet {
        Objects.requireNonNull(windowDuration, "windowDuration");
        searchQueries = List.copyOf(searchQueries);
        domains = List.copyOf(domains);
        errorPhrases = List.copyOf(errorPhrases);
        reasonCodes = List.copyOf(reasonCodes);
        if (eventCount < 0
                || searchQueryCount < 0
                || uniqueDomainCount < 0
                || domainTransitionCount < 0
                || repeatedDomainCount < 0) {
            throw new IllegalArgumentException("feature counts must be non-negative");
        }
        if (windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must not be negative");
        }
    }
}
