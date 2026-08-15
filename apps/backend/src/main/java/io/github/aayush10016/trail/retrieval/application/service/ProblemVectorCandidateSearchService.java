package io.github.aayush10016.trail.retrieval.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery;
import io.github.aayush10016.trail.retrieval.domain.port.ProblemVectorCandidateRepository;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for exact solved Problem Session vector candidate lookup.
 */
public class ProblemVectorCandidateSearchService {

    private final ProblemVectorCandidateRepository repository;

    /**
     * Creates the service.
     */
    public ProblemVectorCandidateSearchService(ProblemVectorCandidateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Finds exact vector candidates owned by the authenticated principal.
     */
    @Transactional(readOnly = true)
    public List<ProblemVectorCandidate> findCandidates(
            AuthenticatedPrincipal principal,
            String generationKey,
            List<Double> vector,
            int limit,
            Duration queryTimeout) {
        Objects.requireNonNull(principal, "principal");
        return repository.findCandidates(
                new ProblemVectorCandidateQuery(principal.userId(), generationKey, vector, limit, queryTimeout));
    }
}
