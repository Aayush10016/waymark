package io.github.aayush10016.trail.retrieval.domain.port;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery;
import java.util.List;

/**
 * Port for exact user-scoped vector candidate lookup.
 */
public interface ProblemVectorCandidateRepository {

    /**
     * Finds candidates from active embeddings for the active model generation.
     */
    List<ProblemVectorCandidate> findCandidates(ProblemVectorCandidateQuery query);
}
