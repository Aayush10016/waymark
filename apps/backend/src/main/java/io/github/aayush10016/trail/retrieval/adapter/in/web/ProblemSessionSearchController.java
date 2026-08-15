package io.github.aayush10016.trail.retrieval.adapter.in.web;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import io.github.aayush10016.trail.retrieval.application.service.SolvedProblemLexicalSearchService;
import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import java.time.Instant;
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
 * HTTP adapter for sensitive solved Problem Session search requests.
 */
@RestController
@RequestMapping("/api/v1/problem-session-searches")
public class ProblemSessionSearchController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAXIMUM_PAGE_SIZE = 50;
    private static final String LEXICAL_DEGRADATION_MODE = "LEXICAL";

    private final AccessTokenVerifier accessTokenVerifier;
    private final SolvedProblemLexicalSearchService searchService;

    /**
     * Creates the controller.
     */
    public ProblemSessionSearchController(
            AccessTokenVerifier accessTokenVerifier, SolvedProblemLexicalSearchService searchService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.searchService = Objects.requireNonNull(searchService, "searchService");
    }

    /**
     * Searches the authenticated user's solved Problem Sessions using POST body input only.
     */
    @PostMapping
    public ProblemSessionSearchResponse search(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) ProblemSessionSearchRequest request) {
        var principal = authenticate(authorization);
        var safeRequest = request == null ? ProblemSessionSearchRequest.empty() : request;
        var pageSize = safeRequest.validatedPageSize();
        var filters = safeRequest.safeFilters();
        var fingerprint = SearchCursorCodec.fingerprint(
                safeRequest.query(), filters.technologies(), filters.errorPhrases(), filters.problemTypes());
        var offset = SearchCursorCodec.decodeOffset(safeRequest.cursor(), fingerprint);
        var results = searchService.search(
                principal,
                safeRequest.query(),
                filters.technologies(),
                filters.errorPhrases(),
                filters.problemTypes(),
                fetchLimit(pageSize),
                offset);
        var hasNextPage = results.size() > pageSize;
        var page = results.stream().limit(pageSize).toList();
        var nextCursor = hasNextPage ? SearchCursorCodec.encode(fingerprint, offset + pageSize) : null;
        return ProblemSessionSearchResponse.from(page, nextCursor);
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw RetrievalApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(RetrievalApiException::unauthorized);
    }

    private static int fetchLimit(int pageSize) {
        return Math.min(SolvedProblemSearchQuery.MAXIMUM_LIMIT, pageSize + 1);
    }

    /**
     * Search request body.
     */
    public record ProblemSessionSearchRequest(
            String query, ProblemSessionSearchFilters filters, Integer pageSize, String cursor) {

        static ProblemSessionSearchRequest empty() {
            return new ProblemSessionSearchRequest(null, null, null, null);
        }

        int validatedPageSize() {
            var value = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
            if (value < 1 || value > MAXIMUM_PAGE_SIZE) {
                throw RetrievalApiException.badRequest(
                        "SEARCH_PAGE_SIZE_OUT_OF_RANGE",
                        "Search page size must be between 1 and " + MAXIMUM_PAGE_SIZE + ".");
            }
            return value;
        }

        ProblemSessionSearchFilters safeFilters() {
            return filters == null ? ProblemSessionSearchFilters.empty() : filters;
        }
    }

    /**
     * Search filter request body.
     */
    public record ProblemSessionSearchFilters(
            List<String> technologies, List<String> errorPhrases, List<String> problemTypes) {

        static ProblemSessionSearchFilters empty() {
            return new ProblemSessionSearchFilters(List.of(), List.of(), List.of());
        }
    }

    /**
     * Search response body.
     */
    public record ProblemSessionSearchResponse(
            List<ProblemSessionSearchResultResponse> results, String nextCursor, String degradationMode) {

        static ProblemSessionSearchResponse from(List<SolvedProblemSearchResult> results, String nextCursor) {
            return new ProblemSessionSearchResponse(
                    results.stream().map(ProblemSessionSearchResultResponse::from).toList(),
                    nextCursor,
                    LEXICAL_DEGRADATION_MODE);
        }
    }

    /**
     * Search result response body.
     */
    public record ProblemSessionSearchResultResponse(
            UUID problemSessionId,
            String title,
            String finalSolution,
            String generatedSummary,
            List<String> technologies,
            List<String> errorPhrases,
            String problemType,
            List<SearchEvidenceCode> evidenceCodes,
            Instant updatedAt) {

        static ProblemSessionSearchResultResponse from(SolvedProblemSearchResult result) {
            return new ProblemSessionSearchResultResponse(
                    result.problemSessionId(),
                    result.title(),
                    result.finalSolution(),
                    result.generatedSummary(),
                    result.technologies(),
                    result.errorPhrases(),
                    result.problemType(),
                    result.evidenceCodes(),
                    result.updatedAt());
        }
    }
}
