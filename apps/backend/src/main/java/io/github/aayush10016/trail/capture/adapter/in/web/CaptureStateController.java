package io.github.aayush10016.trail.capture.adapter.in.web;

import io.github.aayush10016.trail.capture.application.dto.CaptureStateSummaryResponse;
import io.github.aayush10016.trail.capture.application.service.CaptureStateSummaryService;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for backend capture-state summaries.
 */
@RestController
@RequestMapping("/api/v1/capture-state")
public class CaptureStateController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final CaptureStateSummaryService summaryService;

    /**
     * Creates the controller.
     */
    public CaptureStateController(
            AccessTokenVerifier accessTokenVerifier, CaptureStateSummaryService summaryService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.summaryService = Objects.requireNonNull(summaryService, "summaryService");
    }

    /**
     * Returns current backend capture-state summary for the authenticated user.
     */
    @GetMapping
    public CaptureStateSummaryResponse getCaptureState(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return summaryService.read(authenticate(authorization));
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw CaptureApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(CaptureApiException::unauthorized);
    }
}
