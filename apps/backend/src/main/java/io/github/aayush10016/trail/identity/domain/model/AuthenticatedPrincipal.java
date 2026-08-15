package io.github.aayush10016.trail.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated identity principal used for access-token issuance.
 *
 * @param userId immutable authenticated user identifier
 */
public record AuthenticatedPrincipal(UUID userId) {

    /**
     * Creates a validated authenticated principal.
     */
    public AuthenticatedPrincipal {
        Objects.requireNonNull(userId, "userId");
    }
}
