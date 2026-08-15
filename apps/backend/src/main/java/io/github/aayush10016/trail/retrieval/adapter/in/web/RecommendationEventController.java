package io.github.aayush10016.trail.retrieval.adapter.in.web;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.retrieval.application.dto.RecommendationEventRequest;
import io.github.aayush10016.trail.retrieval.application.dto.RecommendationEventResponse;
import io.github.aayush10016.trail.retrieval.application.service.RecommendationEventService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for recommendation lifecycle events.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationEventController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final RecommendationEventService eventService;

    /**
     * Creates the controller.
     */
    public RecommendationEventController(
            AccessTokenVerifier accessTokenVerifier, RecommendationEventService eventService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.eventService = Objects.requireNonNull(eventService, "eventService");
    }

    /**
     * Records one allowlisted client-submitted recommendation event.
     */
    @PostMapping("/{recommendationId}/events")
    public ResponseEntity<RecommendationEventResponse> recordEvent(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("recommendationId") UUID recommendationId,
            @RequestBody RecommendationEventRequest request) {
        var response = eventService.recordClientOutcome(authenticate(authorization), recommendationId, request.outcome());
        return ResponseEntity.ok(response);
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw RetrievalApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(RetrievalApiException::unauthorized);
    }
}
