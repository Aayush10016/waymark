package io.github.aayush10016.trail.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.capture.application.command.CaptureEventSource;
import io.github.aayush10016.trail.capture.application.command.CaptureResourceType;
import io.github.aayush10016.trail.capture.application.command.IngestCaptureBatchCommand;
import io.github.aayush10016.trail.capture.application.command.IngestCaptureEventCommand;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventIngestionStatus;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventRejectionCode;
import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationPolicy;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureEventRepository;
import io.github.aayush10016.trail.capture.domain.service.UrlQuerySanitizer;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaptureBatchIngestionServiceTest {

    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("1c9b54f5-97b1-47b6-a7a3-7ed4d398b035"));

    private final FakeConsentRepository consentRepository = new FakeConsentRepository();
    private final FakeEventRepository eventRepository = new FakeEventRepository();
    private final FakeProblemSessionRepository problemSessionRepository = new FakeProblemSessionRepository();
    private final CaptureBatchIngestionService service =
            new CaptureBatchIngestionService(
                    consentRepository,
                    eventRepository,
                    problemSessionRepository,
                    new CaptureIngestionValidator(new UrlQuerySanitizer(new CaptureSanitizationPolicy("test-policy", List.of()))));

    @Test
    void rejectsEveryEventWhenConsentIsMissing() {
        consentRepository.active = false;

        var result = service.ingest(PRINCIPAL, batch(event("event-1")));

        assertThat(result.results()).containsExactly(
                new io.github.aayush10016.trail.capture.application.dto.CaptureEventIngestionResult(
                        "event-1",
                        CaptureEventIngestionStatus.REJECTED,
                        CaptureEventRejectionCode.CONSENT_REQUIRED));
        assertThat(eventRepository.events).isEmpty();
    }

    @Test
    void acceptsValidEventsAfterBackendSanitation() {
        consentRepository.active = true;

        var result = service.ingest(
                PRINCIPAL,
                batch(event("event-1", "https://example.test/docs?utm_source=fixture&topic=cors")));

        assertThat(result.results()).extracting("status").containsExactly(CaptureEventIngestionStatus.ACCEPTED);
        assertThat(eventRepository.events).singleElement().satisfies(event -> {
            assertThat(event.clientEventId()).isEqualTo("event-1");
            assertThat(event.canonicalUrl()).isEqualTo("https://example.test/docs?topic=cors");
            assertThat(event.policyVersion()).isEqualTo("test-policy");
            assertThat(event.retentionExpiresAt()).isEqualTo(Instant.parse("2026-07-13T08:00:00Z").plusSeconds(604800));
        });
    }

    @Test
    void persistsOwnedProblemSessionAssociation() {
        consentRepository.active = true;
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        problemSessionRepository.insert(PRINCIPAL, problemSessionSnapshot(problemSessionId));

        var result = service.ingest(
                PRINCIPAL,
                batch(event("event-1", "https://example.test/docs", problemSessionId)));

        assertThat(result.results()).extracting("status").containsExactly(CaptureEventIngestionStatus.ACCEPTED);
        assertThat(eventRepository.events).singleElement().satisfies(event ->
                assertThat(event.problemSessionId()).isEqualTo(problemSessionId));
    }

    @Test
    void rejectsUnownedProblemSessionAssociationBeforePersistence() {
        consentRepository.active = true;
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000002");

        var result = service.ingest(
                PRINCIPAL,
                batch(event("event-1", "https://example.test/docs", problemSessionId)));

        assertThat(result.results()).extracting("status").containsExactly(CaptureEventIngestionStatus.REJECTED);
        assertThat(result.results().get(0).rejectionCode()).isEqualTo(CaptureEventRejectionCode.INVALID_METADATA);
        assertThat(eventRepository.events).isEmpty();
    }

    @Test
    void returnsDuplicateResultWhenRepositoryAlreadyHasEvent() {
        consentRepository.active = true;
        eventRepository.duplicateClientEventIds.add("event-1");

        var result = service.ingest(PRINCIPAL, batch(event("event-1")));

        assertThat(result.results()).extracting("status").containsExactly(CaptureEventIngestionStatus.DUPLICATE);
    }

    @Test
    void partiallyRejectsUnsafeEventsWithoutBlockingValidEvents() {
        consentRepository.active = true;

        var result = service.ingest(
                PRINCIPAL,
                batch(
                        event("event-1", "https://example.test/docs"),
                        event("event-2", "https://user:password@example.test/private")));

        assertThat(result.results()).extracting("status")
                .containsExactly(CaptureEventIngestionStatus.ACCEPTED, CaptureEventIngestionStatus.REJECTED);
        assertThat(result.results().get(1).rejectionCode()).isEqualTo(CaptureEventRejectionCode.UNSAFE_URL);
        assertThat(eventRepository.events).hasSize(1);
    }

    @Test
    void returnsStructuredRejectionsForInvalidEventMetadata() {
        consentRepository.active = true;

        var missingClientInstanceResult =
                service.ingest(PRINCIPAL, new IngestCaptureBatchCommand("", List.of(event("event-1"))));
        var missingEventTimeResult =
                service.ingest(
                        PRINCIPAL,
                        batch(new IngestCaptureEventCommand(
                                "event-2",
                                1L,
                                "tab-session",
                                null,
                                null,
                                CaptureEventSource.WEB_NAVIGATION_COMMITTED,
                                CaptureResourceType.DOCUMENTATION,
                                "link",
                                "https://example.test/docs",
                                "Example docs",
                                100D,
                                null,
                                List.of())));
        var missingSourceResult =
                service.ingest(
                        PRINCIPAL,
                        batch(new IngestCaptureEventCommand(
                                "event-3",
                                1L,
                                "tab-session",
                                null,
                                null,
                                null,
                                CaptureResourceType.DOCUMENTATION,
                                "link",
                                "https://example.test/docs",
                                "Example docs",
                                100D,
                                Instant.parse("2026-07-13T08:00:00Z"),
                                List.of())));

        assertThat(missingClientInstanceResult.results().get(0).rejectionCode())
                .isEqualTo(CaptureEventRejectionCode.INVALID_CLIENT_INSTANCE_ID);
        assertThat(missingEventTimeResult.results().get(0).rejectionCode())
                .isEqualTo(CaptureEventRejectionCode.INVALID_EVENT_TIME);
        assertThat(missingSourceResult.results().get(0).rejectionCode())
                .isEqualTo(CaptureEventRejectionCode.INVALID_METADATA);
        assertThat(eventRepository.events).isEmpty();
    }

    @Test
    void rejectsOversizedBatchesBeforePersistence() {
        consentRepository.active = true;
        var events = new ArrayList<IngestCaptureEventCommand>();
        for (var index = 0; index < CaptureBatchIngestionService.MAXIMUM_BATCH_SIZE + 1; index += 1) {
            events.add(event("event-" + index));
        }

        assertThatThrownBy(() -> service.ingest(PRINCIPAL, new IngestCaptureBatchCommand("client-instance", events)))
                .isInstanceOf(CaptureBatchTooLargeException.class);
        assertThat(eventRepository.events).isEmpty();
    }

    private IngestCaptureBatchCommand batch(IngestCaptureEventCommand... events) {
        return new IngestCaptureBatchCommand("client-instance", List.of(events));
    }

    private IngestCaptureEventCommand event(String clientEventId) {
        return event(clientEventId, "https://example.test/docs");
    }

    private IngestCaptureEventCommand event(String clientEventId, String url) {
        return event(clientEventId, url, null);
    }

    private IngestCaptureEventCommand event(String clientEventId, String url, UUID problemSessionId) {
        return new IngestCaptureEventCommand(
                clientEventId,
                1L,
                "tab-session",
                null,
                problemSessionId,
                CaptureEventSource.WEB_NAVIGATION_COMMITTED,
                CaptureResourceType.DOCUMENTATION,
                "link",
                url,
                "Example docs",
                100D,
                Instant.parse("2026-07-13T08:00:00Z"),
                List.of("from_address_bar"));
    }

    private ProblemSessionSnapshot problemSessionSnapshot(UUID problemSessionId) {
        return new ProblemSessionSnapshot(
                problemSessionId,
                ProblemSessionSource.MANUAL,
                ProblemSession.candidate(),
                0L,
                "Spring CORS",
                null,
                null,
                Instant.parse("2026-07-13T08:00:00Z"),
                null,
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T08:00:00Z"),
                null);
    }

    private static final class FakeConsentRepository implements CaptureConsentRepository {
        private boolean active = true;

        @Override
        public boolean hasActiveCaptureConsent(AuthenticatedPrincipal principal) {
            return active;
        }

        @Override
        public void recordGrantedCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant grantedAt) {
        }

        @Override
        public void recordWithdrawnCaptureConsent(AuthenticatedPrincipal principal, String policyVersion, Instant withdrawnAt) {
        }
    }

    private static final class FakeEventRepository implements CaptureEventRepository {
        private final List<CaptureEventToPersist> events = new ArrayList<>();
        private final List<String> duplicateClientEventIds = new ArrayList<>();

        @Override
        public StoreCaptureEventResult storeIfAbsent(AuthenticatedPrincipal principal, CaptureEventToPersist event) {
            if (duplicateClientEventIds.contains(event.clientEventId())) {
                return StoreCaptureEventResult.DUPLICATE;
            }
            events.add(event);
            return StoreCaptureEventResult.INSERTED;
        }
    }

    private static final class FakeProblemSessionRepository implements ProblemSessionRepository {
        private final Map<String, ProblemSessionSnapshot> snapshots = new java.util.HashMap<>();

        @Override
        public void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot) {
            snapshots.put(key(principal, snapshot.id()), snapshot);
        }

        @Override
        public Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return Optional.ofNullable(snapshots.get(key(principal, problemSessionId)));
        }

        @Override
        public boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion) {
            throw new UnsupportedOperationException("update is outside capture ingestion tests.");
        }

        private static String key(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return principal.userId() + ":" + problemSessionId;
        }
    }
}
