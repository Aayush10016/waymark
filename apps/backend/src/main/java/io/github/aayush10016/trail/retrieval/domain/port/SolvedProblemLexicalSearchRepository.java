package io.github.aayush10016.trail.retrieval.domain.port;

import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import java.util.List;

/**
 * Outbound port for weighted solved-session lexical/error/technology search.
 */
public interface SolvedProblemLexicalSearchRepository {

    /**
     * Searches user-owned solved Problem Sessions with deterministic evidence.
     */
    List<SolvedProblemSearchResult> search(SolvedProblemSearchQuery query);
}
