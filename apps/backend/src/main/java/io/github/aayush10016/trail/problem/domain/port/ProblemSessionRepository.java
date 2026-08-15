package io.github.aayush10016.trail.problem.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for user-owned Problem Sessions.
 */
public interface ProblemSessionRepository {

    /**
     * Stores a new Problem Session for the authenticated user.
     */
    void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot);

    /**
     * Finds a Problem Session by ID within the authenticated user's ownership boundary.
     */
    Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId);

    /**
     * Updates a Problem Session only if the expected optimistic version still matches.
     *
     * @return true when the row was updated
     */
    boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion);
}
