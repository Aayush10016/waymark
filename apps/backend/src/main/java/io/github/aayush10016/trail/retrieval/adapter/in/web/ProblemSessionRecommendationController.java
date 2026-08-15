package io.github.aayush10016.trail.retrieval.adapter.in.web;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.retrieval.application.dto.ProblemRecommendationDetailResponse;
import io.github.aayush10016.trail.retrieval.application.service.ProblemRecommendationDetailQueryService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for current Problem Session recommendation detail.
 */
@RestController
@RequestMapping("/api/v1/problem-sessions")
public class ProblemSessionRecommendationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final ProblemRecommendationDetailQueryService queryService;

    /**
     * Creates the controller.
     */
    public ProblemSessionRecommendationController(
            AccessTokenVerifier accessTokenVerifier, ProblemRecommendationDetailQueryService queryService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    /**
     * Returns the top eligible recommendation for a current candidate, or 204 when no match exists.
     */
    @GetMapping("/{problemSessionId}/recommendation")
    public ResponseEntity<ProblemRecommendationDetailResponse> getRecommendation(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("problemSessionId") UUID problemSessionId) {
        return queryService
                .findRecommendation(authenticate(authorization), problemSessionId)
                .map(recommendation -> ResponseEntity.ok()
                        .eTag(recommendation.recommendationId().toString())
                        .body(recommendation))
                .orElseGet(() -> ResponseEntity.noContent().build());
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
