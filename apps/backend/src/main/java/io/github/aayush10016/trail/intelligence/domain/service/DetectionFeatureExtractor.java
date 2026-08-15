package io.github.aayush10016.trail.intelligence.domain.service;

import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionFeatureSet;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionReasonCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic feature extractor for sanitized capture-event windows.
 *
 * <p>This class does not score candidates, create Problem Sessions, call AI providers,
 * access persistence, or depend on wall-clock time.
 */
public final class DetectionFeatureExtractor {

    /**
     * Maximum number of events considered for one deterministic feature window.
     */
    public static final int MAXIMUM_EVENTS = 100;

    /**
     * Maximum time span represented by extracted features.
     */
    public static final Duration MAXIMUM_WINDOW_DURATION = Duration.ofMinutes(45);

    /**
     * Maximum normalized search queries retained in the feature set.
     */
    public static final int MAXIMUM_SEARCH_QUERIES = 8;

    /**
     * Maximum unique domains retained in the feature set.
     */
    public static final int MAXIMUM_DOMAINS = 12;

    /**
     * Maximum error phrases retained in the feature set.
     */
    public static final int MAXIMUM_ERROR_PHRASES = 10;

    private static final int MAXIMUM_QUERY_LENGTH = 160;
    private static final int MAXIMUM_DOMAIN_TRANSITIONS = 99;
    private static final int MAXIMUM_REPEATED_DOMAINS = 12;

    private static final Comparator<CaptureEventToPersist> EVENT_ORDER =
            Comparator.comparing(CaptureEventToPersist::occurredAt)
                    .thenComparing(event -> event.clientSequence() == null ? Long.MAX_VALUE : event.clientSequence())
                    .thenComparing(CaptureEventToPersist::clientEventId);

    private static final List<ErrorPhraseRule> ERROR_PHRASE_RULES = List.of(
            new ErrorPhraseRule("nullpointerexception", "nullpointerexception"),
            new ErrorPhraseRule("illegalstateexception", "illegalstateexception"),
            new ErrorPhraseRule("illegalargumentexception", "illegalargumentexception"),
            new ErrorPhraseRule("typeerror", "typeerror"),
            new ErrorPhraseRule("referenceerror", "referenceerror"),
            new ErrorPhraseRule("exception", "exception"),
            new ErrorPhraseRule("not working", "not working"),
            new ErrorPhraseRule("does not work", "does not work"),
            new ErrorPhraseRule("failed", "failed"),
            new ErrorPhraseRule("failure", "failure"),
            new ErrorPhraseRule("error", "error"),
            new ErrorPhraseRule("cors", "cors"),
            new ErrorPhraseRule("401", "401"),
            new ErrorPhraseRule("403", "403"),
            new ErrorPhraseRule("404", "404"),
            new ErrorPhraseRule("500", "500"));

    /**
     * Extracts bounded time, query, domain, and error features from sanitized events.
     *
     * @param events sanitized capture events
     * @return deterministic feature set
     */
    public DetectionFeatureSet extract(List<CaptureEventToPersist> events) {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
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

        var orderedEvents = events.stream()
                .map(event -> Objects.requireNonNull(event, "event"))
                .sorted(EVENT_ORDER)
                .limit(MAXIMUM_EVENTS)
                .toList();

        var domainCounts = new LinkedHashMap<String, Integer>();
        var searchQueries = new LinkedHashSet<String>();
        var errorPhrases = new LinkedHashSet<String>();
        var domainTransitionCount = 0;
        String previousDomain = null;

        for (var event : orderedEvents) {
            var hostname = normalizeDomain(event.hostname());
            if (hostname != null) {
                domainCounts.merge(hostname, 1, Integer::sum);
                if (previousDomain != null && !previousDomain.equals(hostname)) {
                    domainTransitionCount += 1;
                }
                previousDomain = hostname;
            }

            var normalizedQuery = normalizeSearchQuery(event.searchQuery());
            if (normalizedQuery != null && searchQueries.size() < MAXIMUM_SEARCH_QUERIES) {
                searchQueries.add(normalizedQuery);
            }

            collectErrorPhrases(errorPhrases, event.searchQuery());
            collectErrorPhrases(errorPhrases, event.title());
        }

        var domains = domainCounts.keySet().stream().limit(MAXIMUM_DOMAINS).toList();
        var repeatedDomainCount = (int) domainCounts.values().stream().filter(count -> count > 1).count();
        var boundedErrorPhrases = errorPhrases.stream().limit(MAXIMUM_ERROR_PHRASES).toList();

        return new DetectionFeatureSet(
                orderedEvents.size(),
                boundedDuration(orderedEvents),
                countSearchQueryEvents(orderedEvents),
                List.copyOf(searchQueries),
                domainCounts.size(),
                domains,
                Math.min(domainTransitionCount, MAXIMUM_DOMAIN_TRANSITIONS),
                Math.min(repeatedDomainCount, MAXIMUM_REPEATED_DOMAINS),
                boundedErrorPhrases,
                reasonCodes(
                        orderedEvents.size(),
                        searchQueries,
                        domainCounts.size(),
                        domainTransitionCount,
                        repeatedDomainCount,
                        boundedErrorPhrases));
    }

    private static Duration boundedDuration(List<CaptureEventToPersist> orderedEvents) {
        var first = orderedEvents.get(0).occurredAt();
        var last = orderedEvents.get(orderedEvents.size() - 1).occurredAt();
        var duration = Duration.between(first, last);
        if (duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration.compareTo(MAXIMUM_WINDOW_DURATION) > 0 ? MAXIMUM_WINDOW_DURATION : duration;
    }

    private static int countSearchQueryEvents(List<CaptureEventToPersist> orderedEvents) {
        var count = 0;
        for (var event : orderedEvents) {
            if (normalizeSearchQuery(event.searchQuery()) != null) {
                count += 1;
            }
        }
        return count;
    }

    private static List<DetectionReasonCode> reasonCodes(
            int eventCount,
            Set<String> searchQueries,
            int uniqueDomainCount,
            int domainTransitionCount,
            int repeatedDomainCount,
            List<String> errorPhrases) {
        var reasons = new ArrayList<DetectionReasonCode>();
        if (eventCount == 0) {
            reasons.add(DetectionReasonCode.NO_EVENTS);
            return reasons;
        }
        reasons.add(DetectionReasonCode.TIME_WINDOW_PRESENT);
        if (!searchQueries.isEmpty()) {
            reasons.add(DetectionReasonCode.SEARCH_QUERY_PRESENT);
        }
        if (!errorPhrases.isEmpty()) {
            reasons.add(DetectionReasonCode.ERROR_PHRASE_PRESENT);
        }
        if (uniqueDomainCount > 1) {
            reasons.add(DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT);
        }
        if (domainTransitionCount > 0) {
            reasons.add(DetectionReasonCode.DOMAIN_TRANSITION_PRESENT);
        }
        if (repeatedDomainCount > 0) {
            reasons.add(DetectionReasonCode.REPEATED_DOMAIN_PRESENT);
        }
        return reasons;
    }

    private static void collectErrorPhrases(Set<String> errorPhrases, String value) {
        if (value == null || errorPhrases.size() >= MAXIMUM_ERROR_PHRASES) {
            return;
        }
        var normalized = normalizeText(value);
        for (var rule : ERROR_PHRASE_RULES) {
            if (errorPhrases.size() >= MAXIMUM_ERROR_PHRASES) {
                return;
            }
            if (normalized.contains(rule.matchText())) {
                errorPhrases.add(rule.outputText());
            }
        }
    }

    private static String normalizeSearchQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = normalizeText(value);
        if (normalized.length() > MAXIMUM_QUERY_LENGTH) {
            return normalized.substring(0, MAXIMUM_QUERY_LENGTH);
        }
        return normalized;
    }

    private static String normalizeDomain(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record ErrorPhraseRule(String matchText, String outputText) {}
}
