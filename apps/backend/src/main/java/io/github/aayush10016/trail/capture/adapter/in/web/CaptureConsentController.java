package io.github.aayush10016.trail.capture.adapter.in.web;

import io.github.aayush10016.trail.capture.application.dto.CaptureConsentDecisionResponse;
import io.github.aayush10016.trail.capture.application.service.CaptureConsentDecisionService;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for explicit capture consent decisions.
 */
@RestController
@RequestMapping("/api/v1")
public class CaptureConsentController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final CaptureConsentDecisionService consentDecisionService;

    /**
     * Creates the controller.
     */
    public CaptureConsentController(
            AccessTokenVerifier accessTokenVerifier, CaptureConsentDecisionService consentDecisionService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.consentDecisionService = Objects.requireNonNull(consentDecisionService, "consentDecisionService");
    }

    /**
     * Records explicit browser navigation metadata capture consent.
     */
    @PostMapping("/consent-grants")
    public CaptureConsentDecisionResponse grantConsent(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CaptureConsentDecisionRequest request) {
        var principal = authenticate(authorization);
        return consentDecisionService.grant(principal, request.policyVersion(), request.decidedAt());
    }

    /**
     * Records withdrawal of browser navigation metadata capture consent.
     */
    @PostMapping("/consent-withdrawals")
    public CaptureConsentDecisionResponse withdrawConsent(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CaptureConsentDecisionRequest request) {
        var principal = authenticate(authorization);
        return consentDecisionService.withdraw(principal, request.policyVersion(), request.decidedAt());
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
     * Capture consent decision request body.
     */
    public record CaptureConsentDecisionRequest(String policyVersion, Instant decidedAt) {}
}
