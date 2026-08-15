package io.github.aayush10016.trail.problem.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence boundary for deleting one user-owned Problem Session and its derived data.
 */
public interface ProblemSessionDeletionRepository {

    /**
     * Deletes a Problem Session inside the authenticated user's ownership boundary.
     *
     * @param principal authenticated owner
     * @param problemSessionId Problem Session to delete
     * @param deletedAt deletion timestamp used for source-row exclusion
     * @return true when a non-deleted owned session existed and was removed
     */
    boolean delete(AuthenticatedPrincipal principal, UUID problemSessionId, Instant deletedAt);
}
