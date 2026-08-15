package io.github.aayush10016.trail.problem.adapter.in.web;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.problem.application.command.FinishProblemSessionCommand;
import io.github.aayush10016.trail.problem.application.command.SaveProblemSessionSolutionCommand;
import io.github.aayush10016.trail.problem.application.command.StartProblemSessionCommand;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionStateResponse;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionSolutionResponse;
import io.github.aayush10016.trail.problem.application.service.ManualProblemSessionService;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionDeletionService;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionSolutionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for manual Problem Session commands.
 */
@RestController
@RequestMapping("/api/v1/problem-sessions")
public class ProblemSessionCommandController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final ManualProblemSessionService manualProblemSessionService;
    private final ProblemSessionDeletionService problemSessionDeletionService;
    private final ProblemSessionSolutionService problemSessionSolutionService;
    private final Clock clock;

    /**
     * Creates the controller.
     */
    public ProblemSessionCommandController(
            AccessTokenVerifier accessTokenVerifier,
            ManualProblemSessionService manualProblemSessionService,
            ProblemSessionDeletionService problemSessionDeletionService,
            ProblemSessionSolutionService problemSessionSolutionService,
            Clock clock) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.manualProblemSessionService = Objects.requireNonNull(manualProblemSessionService, "manualProblemSessionService");
        this.problemSessionDeletionService = Objects.requireNonNull(problemSessionDeletionService, "problemSessionDeletionService");
        this.problemSessionSolutionService = Objects.requireNonNull(problemSessionSolutionService, "problemSessionSolutionService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Manually starts a Problem Session candidate.
     */
    @PostMapping
    public ResponseEntity<ProblemSessionCommandResponse> startProblemSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) StartProblemSessionRequest request) {
        var principal = authenticate(authorization);
        var safeRequest = request == null ? new StartProblemSessionRequest(null, null, null) : request;
        var result = manualProblemSessionService.start(principal, safeRequest.toCommand(clock.instant()));
        return response(result);
    }

    /**
     * Closes a Problem Session capture boundary.
     */
    @PostMapping("/{problemSessionId}/finish")
    public ResponseEntity<ProblemSessionCommandResponse> finishProblemSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @PathVariable("problemSessionId") UUID problemSessionId,
            @RequestBody(required = false) FinishProblemSessionRequest request) {
        var principal = authenticate(authorization);
        var expectedVersion = parseIfMatch(ifMatch);
        var safeRequest = request == null ? new FinishProblemSessionRequest(null) : request;
        var result = manualProblemSessionService.finish(
                principal, new FinishProblemSessionCommand(problemSessionId, expectedVersion, safeRequest.finishedAtOr(clock.instant())));
        return response(result);
    }

    /**
     * Saves the exact user-authored final solution and marks the Problem Session solved.
     */
    @PutMapping("/{problemSessionId}/solution")
    public ResponseEntity<ProblemSessionSolutionResponse> saveProblemSessionSolution(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("problemSessionId") UUID problemSessionId,
            @RequestBody(required = false) SaveProblemSessionSolutionRequest request) {
        var principal = authenticate(authorization);
        var expectedVersion = parseIfMatch(ifMatch);
        var safeRequest = requireSolutionRequest(request);
        var result = problemSessionSolutionService.save(
                principal,
                new SaveProblemSessionSolutionCommand(
                        problemSessionId,
                        expectedVersion,
                        requireIdempotencyKey(idempotencyKey),
                        safeRequest.finalSolution(),
                        clock.instant()));
        return ResponseEntity.ok()
                .eTag(Long.toString(result.version()))
                .body(result);
    }

    /**
     * Deletes one user-owned Problem Session and its active derived records.
     */
    @DeleteMapping("/{problemSessionId}")
    public ResponseEntity<Void> deleteProblemSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("problemSessionId") UUID problemSessionId) {
        problemSessionDeletionService.delete(authenticate(authorization), problemSessionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private ResponseEntity<ProblemSessionCommandResponse> response(ProblemSessionStateResponse result) {
        return ResponseEntity.ok()
                .eTag(Long.toString(result.version()))
                .body(ProblemSessionCommandResponse.from(result));
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw ProblemApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(ProblemApiException::unauthorized);
    }

    private long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ProblemApiException.preconditionRequired();
        }
        var value = ifMatch.strip();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            var parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw ProblemApiException.badRequest("PROBLEM_SESSION_INVALID_VERSION", "If-Match version is invalid.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw ProblemApiException.badRequest("PROBLEM_SESSION_INVALID_VERSION", "If-Match version is invalid.");
        }
    }

    private SaveProblemSessionSolutionRequest requireSolutionRequest(SaveProblemSessionSolutionRequest request) {
        if (request == null || request.finalSolution() == null) {
            throw ProblemApiException.badRequest("PROBLEM_SESSION_INVALID_REQUEST", "Final solution is required.");
        }
        return request;
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ProblemApiException.badRequest("PROBLEM_SESSION_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
        }
        return idempotencyKey;
    }

    /**
     * Manual start request body.
     */
    public record StartProblemSessionRequest(UUID problemSessionId, String title, Instant startedAt) {

        StartProblemSessionCommand toCommand(Instant fallbackStartedAt) {
            return new StartProblemSessionCommand(problemSessionId, title, startedAt == null ? fallbackStartedAt : startedAt);
        }
    }

    /**
     * Manual finish request body.
     */
    public record FinishProblemSessionRequest(Instant finishedAt) {

        Instant finishedAtOr(Instant fallbackFinishedAt) {
            return finishedAt == null ? fallbackFinishedAt : finishedAt;
        }
    }

    /**
     * Final solution request body.
     */
    public record SaveProblemSessionSolutionRequest(String finalSolution) {}

    /**
     * Problem Session command response body.
     */
    public record ProblemSessionCommandResponse(
            UUID id,
            String status,
            String source,
            long version,
            String title,
            Instant startedAt,
            Instant endedAt,
            Instant updatedAt) {

        static ProblemSessionCommandResponse from(ProblemSessionStateResponse response) {
            return new ProblemSessionCommandResponse(
                    response.id(),
                    response.status().name(),
                    response.source().name(),
                    response.version(),
                    response.title(),
                    response.startedAt(),
                    response.endedAt(),
                    response.updatedAt());
        }
    }
}
