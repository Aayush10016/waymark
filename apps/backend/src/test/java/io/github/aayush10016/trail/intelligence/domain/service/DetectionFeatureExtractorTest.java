package io.github.aayush10016.trail.intelligence.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.intelligence.domain.model.DetectionReasonCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionFeatureExtractorTest {

    private final DetectionFeatureExtractor extractor = new DetectionFeatureExtractor();

    @Test
    void returnsEmptyGoldenFixtureForNoEvents() {
        var features = extractor.extract(List.of());

        assertThat(features.eventCount()).isZero();
        assertThat(features.windowDuration()).isEqualTo(Duration.ZERO);
        assertThat(features.searchQueries()).isEmpty();
        assertThat(features.domains()).isEmpty();
        assertThat(features.errorPhrases()).isEmpty();
        assertThat(features.reasonCodes()).containsExactly(DetectionReasonCode.NO_EVENTS);
    }

    @Test
    void extractsBoundedTimeQueryDomainErrorFeaturesFromGoldenFixture() {
        var events = List.of(
                event(
                        "event-1",
                        1L,
                        "https://www.google.com/search?q=spring+security+cors+not+working",
                        "www.google.com",
                        "spring security cors not working - Google Search",
                        "spring security cors not working",
                        Instant.parse("2026-07-13T08:00:00Z")),
                event(
                        "event-2",
                        2L,
                        "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
                        "docs.spring.io",
                        "CORS :: Spring Security",
                        null,
                        Instant.parse("2026-07-13T08:03:00Z")),
                event(
                        "event-3",
                        3L,
                        "https://stackoverflow.com/questions/123/spring-security-cors-error",
                        "stackoverflow.com",
                        "Spring Security CORS error with JWT filter",
                        null,
                        Instant.parse("2026-07-13T08:08:00Z")),
                event(
                        "event-4",
                        4L,
                        "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
                        "docs.spring.io",
                        "CORS :: Spring Security",
                        null,
                        Instant.parse("2026-07-13T08:12:00Z")));

        var features = extractor.extract(events);

        assertThat(features.eventCount()).isEqualTo(4);
        assertThat(features.windowDuration()).isEqualTo(Duration.ofMinutes(12));
        assertThat(features.searchQueryCount()).isEqualTo(1);
        assertThat(features.searchQueries()).containsExactly("spring security cors not working");
        assertThat(features.uniqueDomainCount()).isEqualTo(3);
        assertThat(features.domains()).containsExactly("www.google.com", "docs.spring.io", "stackoverflow.com");
        assertThat(features.domainTransitionCount()).isEqualTo(3);
        assertThat(features.repeatedDomainCount()).isEqualTo(1);
        assertThat(features.errorPhrases()).containsExactly("not working", "cors", "error");
        assertThat(features.reasonCodes())
                .containsExactly(
                        DetectionReasonCode.TIME_WINDOW_PRESENT,
                        DetectionReasonCode.SEARCH_QUERY_PRESENT,
                        DetectionReasonCode.ERROR_PHRASE_PRESENT,
                        DetectionReasonCode.MULTIPLE_DOMAINS_PRESENT,
                        DetectionReasonCode.DOMAIN_TRANSITION_PRESENT,
                        DetectionReasonCode.REPEATED_DOMAIN_PRESENT);
    }

    @Test
    void identicalInputProducesIdenticalOutput() {
        var events = List.of(
                event(
                        "event-1",
                        1L,
                        "https://docs.example.test/null-pointer",
                        "docs.example.test",
                        "NullPointerException guide",
                        null,
                        Instant.parse("2026-07-13T08:00:00Z")));

        assertThat(extractor.extract(events)).isEqualTo(extractor.extract(events));
    }

    @Test
    void eventOrderingUsesChronologyThenSequenceThenClientEventId() {
        var latest = event(
                "event-3",
                3L,
                "https://stackoverflow.com/questions/123",
                "stackoverflow.com",
                "403 error",
                null,
                Instant.parse("2026-07-13T08:10:00Z"));
        var earliest = event(
                "event-1",
                1L,
                "https://www.google.com/search?q=403+error",
                "www.google.com",
                "403 error - Google Search",
                "403 error",
                Instant.parse("2026-07-13T08:00:00Z"));
        var middle = event(
                "event-2",
                2L,
                "https://docs.example.test/auth",
                "docs.example.test",
                "Auth docs",
                null,
                Instant.parse("2026-07-13T08:05:00Z"));

        var shuffled = extractor.extract(List.of(latest, earliest, middle));
        var chronological = extractor.extract(List.of(earliest, middle, latest));

        assertThat(shuffled).isEqualTo(chronological);
        assertThat(shuffled.domains()).containsExactly("www.google.com", "docs.example.test", "stackoverflow.com");
    }

    @Test
    void boundsEventsDurationQueriesDomainsAndErrorPhrases() {
        var events = new ArrayList<CaptureEventToPersist>();
        for (var index = 0; index < 120; index += 1) {
            events.add(event(
                    "event-" + index,
                    (long) index,
                    "https://domain-" + index + ".example.test/path",
                    "domain-" + index + ".example.test",
                    titleFor(index),
                    "query " + index,
                    Instant.parse("2026-07-13T08:00:00Z").plus(Duration.ofMinutes(index))));
        }

        var features = extractor.extract(events);

        assertThat(features.eventCount()).isEqualTo(DetectionFeatureExtractor.MAXIMUM_EVENTS);
        assertThat(features.windowDuration()).isEqualTo(DetectionFeatureExtractor.MAXIMUM_WINDOW_DURATION);
        assertThat(features.searchQueries()).hasSize(DetectionFeatureExtractor.MAXIMUM_SEARCH_QUERIES);
        assertThat(features.domains()).hasSize(DetectionFeatureExtractor.MAXIMUM_DOMAINS);
        assertThat(features.errorPhrases()).hasSizeLessThanOrEqualTo(DetectionFeatureExtractor.MAXIMUM_ERROR_PHRASES);
        assertThat(features.domainTransitionCount()).isLessThanOrEqualTo(99);
    }

    @Test
    void insufficientEvidenceDoesNotInventSignals() {
        var features = extractor.extract(List.of(event(
                "event-1",
                1L,
                "https://docs.example.test/guide",
                "docs.example.test",
                "Reference guide",
                null,
                Instant.parse("2026-07-13T08:00:00Z"))));

        assertThat(features.searchQueries()).isEmpty();
        assertThat(features.errorPhrases()).isEmpty();
        assertThat(features.domainTransitionCount()).isZero();
        assertThat(features.reasonCodes()).containsExactly(DetectionReasonCode.TIME_WINDOW_PRESENT);
    }

    @Test
    void rejectsNullInputAndNullEventsPredictably() {
        assertThatThrownBy(() -> extractor.extract(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> extractor.extract(listWithNullEvent())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void doesNotMutateInputOrExposeMutableOutputCollections() {
        var events = new ArrayList<CaptureEventToPersist>();
        events.add(event(
                "event-1",
                1L,
                "https://www.google.com/search?q=cors+error",
                "www.google.com",
                "cors error",
                "cors error",
                Instant.parse("2026-07-13T08:00:00Z")));

        var features = extractor.extract(events);
        events.clear();

        assertThat(features.eventCount()).isEqualTo(1);
        assertThatThrownBy(() -> features.domains().add("mutated.example.test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static String titleFor(int index) {
        return switch (index % 6) {
            case 0 -> "NullPointerException";
            case 1 -> "TypeError";
            case 2 -> "ReferenceError";
            case 3 -> "CORS not working";
            case 4 -> "403 failed";
            default -> "500 error";
        };
    }

    private static List<CaptureEventToPersist> listWithNullEvent() {
        var events = new ArrayList<CaptureEventToPersist>();
        events.add(null);
        return events;
    }

    private static CaptureEventToPersist event(
            String clientEventId,
            Long clientSequence,
            String canonicalUrl,
            String hostname,
            String title,
            String searchQuery,
            Instant occurredAt) {
        return new CaptureEventToPersist(
                clientEventId,
                clientSequence,
                "extension-instance-1",
                "tab-session-1",
                null,
                null,
                "WEB_NAVIGATION_COMMITTED",
                "DOCUMENTATION",
                "link",
                canonicalUrl,
                hostname,
                title,
                searchQuery == null ? null : "google",
                searchQuery,
                List.of(),
                List.of(),
                "url-query-sanitation-v1",
                100D,
                occurredAt,
                occurredAt.plus(Duration.ofDays(7)));
    }
}
