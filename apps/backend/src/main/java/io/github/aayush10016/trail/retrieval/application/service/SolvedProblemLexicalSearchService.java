package io.github.aayush10016.trail.retrieval.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import io.github.aayush10016.trail.retrieval.domain.port.SolvedProblemLexicalSearchRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for deterministic solved Problem Session lexical search.
 */
public class SolvedProblemLexicalSearchService {

    private final SolvedProblemLexicalSearchRepository repository;

    /**
     * Creates the search service.
     */
    public SolvedProblemLexicalSearchService(SolvedProblemLexicalSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Searches solved sessions owned by the authenticated principal.
     */
    @Transactional(readOnly = true)
    public List<SolvedProblemSearchResult> search(
            AuthenticatedPrincipal principal,
            String queryText,
            List<String> technologies,
            List<String> errorPhrases,
            int limit) {
        Objects.requireNonNull(principal, "principal");
        return repository.search(new SolvedProblemSearchQuery(
                principal.userId(),
                queryText,
                technologies,
                errorPhrases,
                List.of(),
                limit,
                0));
    }

    /**
     * Searches solved sessions from an explicit offset for cursor-paginated API use.
     */
    @Transactional(readOnly = true)
    public List<SolvedProblemSearchResult> search(
            AuthenticatedPrincipal principal,
            String queryText,
            List<String> technologies,
            List<String> errorPhrases,
            List<String> problemTypes,
            int limit,
            int offset) {
        Objects.requireNonNull(principal, "principal");
        return repository.search(new SolvedProblemSearchQuery(
                principal.userId(),
                queryText,
                technologies,
                errorPhrases,
                problemTypes,
                limit,
                offset));
    }
}
