package io.github.aayush10016.trail.problem.adapter.in.web;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionDetailResponse;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionDetailQueryService;
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
 * HTTP adapter for user-owned Problem Session detail queries.
 */
@RestController
@RequestMapping("/api/v1/problem-sessions")
public class ProblemSessionDetailController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final ProblemSessionDetailQueryService queryService;

    /**
     * Creates the controller.
     */
    public ProblemSessionDetailController(
            AccessTokenVerifier accessTokenVerifier, ProblemSessionDetailQueryService queryService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    /**
     * Returns bounded detail and active ordered artifacts for an owned Problem Session.
     */
    @GetMapping("/{problemSessionId}")
    public ResponseEntity<ProblemSessionDetailResponse> getProblemSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("problemSessionId") UUID problemSessionId) {
        var result = queryService.getDetail(authenticate(authorization), problemSessionId);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.version()))
                .body(result);
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw ProblemApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(ProblemApiException::unauthorized);
    }
}
