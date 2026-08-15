package io.github.aayush10016.trail.capture.adapter.in.web;

import io.github.aayush10016.trail.capture.application.command.CaptureEventSource;
import io.github.aayush10016.trail.capture.application.command.CaptureResourceType;
import io.github.aayush10016.trail.capture.application.command.IngestCaptureBatchCommand;
import io.github.aayush10016.trail.capture.application.command.IngestCaptureEventCommand;
import io.github.aayush10016.trail.capture.application.dto.CaptureBatchIngestionResult;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventIngestionResult;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventIngestionStatus;
import io.github.aayush10016.trail.capture.application.dto.CaptureEventRejectionCode;
import io.github.aayush10016.trail.capture.application.service.CaptureBatchIngestionService;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for capture batch ingestion.
 */
@RestController
@RequestMapping("/api/v1/capture-batches")
public class CaptureBatchController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final CaptureBatchIngestionService ingestionService;

    /**
     * Creates the controller.
     */
    public CaptureBatchController(
            AccessTokenVerifier accessTokenVerifier, CaptureBatchIngestionService ingestionService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService");
    }

    /**
     * Ingests a bounded batch of sanitized extension capture events.
     */
    @PostMapping
    public CaptureBatchResponse ingestCaptureBatch(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CaptureBatchRequest request) {
        var principal = authenticate(authorization);
        var result = ingestionService.ingest(principal, request.toCommand());
        return CaptureBatchResponse.from(result);
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw CaptureApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(CaptureApiException::unauthorized);
    }

    /**
     * Capture batch request body.
     */
    public record CaptureBatchRequest(String clientInstanceId, List<CaptureEventRequest> events) {

        IngestCaptureBatchCommand toCommand() {
            if (events == null) {
                throw CaptureApiException.badRequest("CAPTURE_INVALID_REQUEST", "Capture events are required.");
            }
            return new IngestCaptureBatchCommand(
                    clientInstanceId,
                    events.stream().map(CaptureEventRequest::toCommand).toList());
        }
    }

    /**
     * Capture event request body.
     */
    public record CaptureEventRequest(
            String clientEventId,
            Long clientSequence,
            String tabSessionId,
            String parentClientEventId,
            UUID problemSessionId,
            CaptureEventSource source,
            CaptureResourceType resourceType,
            String navigationType,
            String url,
            String title,
            Double browserEventTimeMilliseconds,
            Instant occurredAt,
            List<String> transitionQualifiers) {

        IngestCaptureEventCommand toCommand() {
            return new IngestCaptureEventCommand(
                    clientEventId,
                    clientSequence,
                    tabSessionId,
                    parentClientEventId,
                    problemSessionId,
                    source,
                    resourceType,
                    navigationType,
                    url,
                    title,
                    browserEventTimeMilliseconds,
                    occurredAt,
                    transitionQualifiers);
        }
    }

    /**
     * Capture batch response body.
     */
    public record CaptureBatchResponse(
            List<CaptureAcceptedEventResponse> accepted,
            List<CaptureDuplicateEventResponse> duplicate,
            List<CaptureRejectedEventResponse> rejected) {

        static CaptureBatchResponse from(CaptureBatchIngestionResult result) {
            var accepted = new ArrayList<CaptureAcceptedEventResponse>();
            var duplicate = new ArrayList<CaptureDuplicateEventResponse>();
            var rejected = new ArrayList<CaptureRejectedEventResponse>();

            for (var eventResult : result.results()) {
                addResult(eventResult, accepted, duplicate, rejected);
            }

            return new CaptureBatchResponse(List.copyOf(accepted), List.copyOf(duplicate), List.copyOf(rejected));
        }

        private static void addResult(
                CaptureEventIngestionResult eventResult,
                List<CaptureAcceptedEventResponse> accepted,
                List<CaptureDuplicateEventResponse> duplicate,
                List<CaptureRejectedEventResponse> rejected) {
            if (eventResult.status() == CaptureEventIngestionStatus.ACCEPTED) {
                accepted.add(new CaptureAcceptedEventResponse(eventResult.clientEventId()));
                return;
            }
            if (eventResult.status() == CaptureEventIngestionStatus.DUPLICATE) {
                duplicate.add(new CaptureDuplicateEventResponse(eventResult.clientEventId()));
                return;
            }
            rejected.add(new CaptureRejectedEventResponse(eventResult.clientEventId(), eventResult.rejectionCode()));
        }
    }

    /**
     * Accepted event response.
     */
    public record CaptureAcceptedEventResponse(String clientEventId) {}

    /**
     * Duplicate event response.
     */
    public record CaptureDuplicateEventResponse(String clientEventId) {}

    /**
     * Rejected event response.
     */
    public record CaptureRejectedEventResponse(String clientEventId, CaptureEventRejectionCode code) {}
}
