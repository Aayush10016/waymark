package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistence boundary for reauthenticating and purging one account.
 */
public interface AccountDeletionRepository {

    /**
     * Loads the active account password hash for reauthentication.
     */
    Optional<PasswordHashRecord> findActivePasswordHash(AuthenticatedPrincipal principal);

    /**
     * Revokes all active refresh sessions for the account before purge.
     */
    void revokeRefreshSessions(AuthenticatedPrincipal principal, Instant revokedAt);

    /**
     * Purges the account and user-owned rows through database ownership cascades.
     *
     * @return true when the account row was removed
     */
    boolean purgeAccount(AuthenticatedPrincipal principal);
}
