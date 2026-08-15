package io.github.aayush10016.trail.capture.application.service;

import io.github.aayush10016.trail.capture.application.command.IngestCaptureBatchCommand;
import io.github.aayush10016.trail.capture.application.command.IngestCaptureEventCommand;
import io.github.aayush10016.trail.capture.application.dto.AcceptedCaptureIngestion;
import io.github.aayush10016.trail.capture.application.dto.CaptureBatchIngestionResult;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventIngestionResult;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventRejectionCode;
import io.github.aayush10016.trail.capture.application.dto.CaptureIngestionRejectionCode;
import io.github.aayush10016.trail.capture.application.dto.RejectedCaptureIngestion;
import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureEventRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case for authenticated capture batch ingestion.
 */
public class CaptureBatchIngestionService {

    /**
     * Maximum number of events accepted in one capture batch.
     */
    public static final int MAXIMUM_BATCH_SIZE = 50;

    private static final Duration RAW_CAPTURE_RETENTION = Duration.ofDays(7);

    private final CaptureConsentRepository consentRepository;
    private final CaptureEventRepository eventRepository;
    private final ProblemSessionRepository problemSessionRepository;
    private final CaptureIngestionValidator validator;

    /**
     * Creates the capture batch ingestion service.
     *
     * @param consentRepository consent repository port
     * @param eventRepository event repository port
     * @param validator backend sanitation validator
     */
    public CaptureBatchIngestionService(
            CaptureConsentRepository consentRepository,
            CaptureEventRepository eventRepository,
            ProblemSessionRepository problemSessionRepository,
            CaptureIngestionValidator validator) {
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.problemSessionRepository = Objects.requireNonNull(problemSessionRepository, "problemSessionRepository");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Processes one authenticated capture batch.
     *
     * @param principal authenticated user
     * @param command capture batch command
     * @return per-event ingestion result
     */
    @Transactional
    public CaptureBatchIngestionResult ingest(AuthenticatedPrincipal principal, IngestCaptureBatchCommand command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        if (command.events().size() > MAXIMUM_BATCH_SIZE) {
            throw new CaptureBatchTooLargeException(MAXIMUM_BATCH_SIZE);
        }

        if (!consentRepository.hasActiveCaptureConsent(principal)) {
            return rejectAllForMissingConsent(command);
        }

        var results = new ArrayList<CaptureEventIngestionResult>();
        for (var event : command.events()) {
            results.add(processEvent(principal, command.clientInstanceId(), event));
        }
        return new CaptureBatchIngestionResult(results);
    }

    private CaptureBatchIngestionResult rejectAllForMissingConsent(IngestCaptureBatchCommand command) {
        var results = command.events().stream()
                .map(event -> CaptureEventIngestionResult.rejected(
                        event.clientEventId(), CaptureEventRejectionCode.CONSENT_REQUIRED))
                .toList();
        return new CaptureBatchIngestionResult(results);
    }

    private CaptureEventIngestionResult processEvent(
            AuthenticatedPrincipal principal, String clientInstanceId, IngestCaptureEventCommand event) {
        var basicRejection = validateBasicFields(clientInstanceId, event);
        if (basicRejection != null) {
            return CaptureEventIngestionResult.rejected(event.clientEventId(), basicRejection);
        }
        if (event.problemSessionId() != null
                && problemSessionRepository.findById(principal, event.problemSessionId()).isEmpty()) {
            return CaptureEventIngestionResult.rejected(
                    event.clientEventId(), CaptureEventRejectionCode.INVALID_METADATA);
        }

        var input = new HashMap<String, Object>();
        input.put("url", event.url());
        if (event.title() != null) {
            input.put("title", event.title());
        }
        var validationResult = validator.validate(input);
        if (validationResult instanceof RejectedCaptureIngestion rejected) {
            return CaptureEventIngestionResult.rejected(event.clientEventId(), mapRejection(rejected.code()));
        }

        var accepted = (AcceptedCaptureIngestion) validationResult;
        var sanitized = accepted.sanitizedEvent();
        var eventToPersist =
                new CaptureEventToPersist(
                        event.clientEventId(),
                        event.clientSequence(),
                        clientInstanceId,
                        event.tabSessionId(),
                        event.parentClientEventId(),
                        event.problemSessionId(),
                        event.source().name(),
                        event.resourceType().name(),
                        normalizeOptionalText(event.navigationType()),
                        sanitized.canonicalUrl(),
                        sanitized.hostname(),
                        sanitized.title(),
                        sanitized.searchEngine(),
                        sanitized.searchQuery(),
                        sanitized.removedQueryParameters(),
                        event.transitionQualifiers(),
                        sanitized.policyVersion(),
                        event.browserEventTimeMilliseconds(),
                        event.occurredAt(),
                        event.occurredAt().plus(RAW_CAPTURE_RETENTION));

        var storeResult = eventRepository.storeIfAbsent(principal, eventToPersist);
        return switch (storeResult) {
            case INSERTED -> CaptureEventIngestionResult.accepted(event.clientEventId());
            case DUPLICATE -> CaptureEventIngestionResult.duplicate(event.clientEventId());
        };
    }

    private CaptureEventRejectionCode validateBasicFields(String clientInstanceId, IngestCaptureEventCommand event) {
        if (clientInstanceId == null || clientInstanceId.isBlank()) {
            return CaptureEventRejectionCode.INVALID_CLIENT_INSTANCE_ID;
        }
        if (event.clientEventId() == null || event.clientEventId().isBlank()) {
            return CaptureEventRejectionCode.INVALID_CLIENT_EVENT_ID;
        }
        if (event.occurredAt() == null) {
            return CaptureEventRejectionCode.INVALID_EVENT_TIME;
        }
        if (event.source() == null || event.resourceType() == null) {
            return CaptureEventRejectionCode.INVALID_METADATA;
        }
        if (event.browserEventTimeMilliseconds() != null && event.browserEventTimeMilliseconds() < 0) {
            return CaptureEventRejectionCode.INVALID_EVENT_TIME;
        }
        if (event.clientSequence() != null && event.clientSequence() < 0) {
            return CaptureEventRejectionCode.INVALID_METADATA;
        }
        return null;
    }

    private CaptureEventRejectionCode mapRejection(CaptureIngestionRejectionCode code) {
        return switch (code) {
            case MISSING_URL, INVALID_FIELD_TYPE, URL_TOO_LARGE, TITLE_TOO_LARGE, UNKNOWN_FIELD ->
                CaptureEventRejectionCode.INVALID_URL;
            case UNSAFE_URL -> CaptureEventRejectionCode.UNSAFE_URL;
        };
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
