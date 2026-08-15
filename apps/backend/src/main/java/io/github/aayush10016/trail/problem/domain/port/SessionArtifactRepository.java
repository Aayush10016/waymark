package io.github.aayush10016.trail.problem.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence boundary for durable ordered Problem Session artifacts.
 */
public interface SessionArtifactRepository {

    /**
     * Maximum artifacts promoted from raw sanitized capture for a solved session.
     */
    int MAXIMUM_PROMOTED_CAPTURE_ARTIFACTS = 100;

    /**
     * Stores an artifact for a Problem Session owned by the authenticated user.
     */
    void insert(AuthenticatedPrincipal principal, SessionArtifact artifact);

    /**
     * Copies sanitized capture events associated with the Problem Session into durable ordered artifacts.
     *
     * @return number of artifacts inserted
     */
    int promoteAssociatedCaptureEvents(AuthenticatedPrincipal principal, UUID problemSessionId);

    /**
     * Reads active artifacts ordered by stable position.
     */
    List<SessionArtifact> findActiveByProblemSession(AuthenticatedPrincipal principal, UUID problemSessionId);

    /**
     * Marks an artifact removed inside the authenticated user's ownership boundary.
     *
     * @return true when an active artifact was marked removed
     */
    boolean markRemoved(AuthenticatedPrincipal principal, UUID problemSessionId, UUID artifactId, Instant removedAt);
}
