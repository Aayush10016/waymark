package io.github.aayush10016.trail.identity.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedRefreshToken;

/**
 * Application boundary for creating an initial refresh session after login.
 */
public interface RefreshSessionCreator {

    /**
     * Creates an initial refresh session for an authenticated principal.
     *
     * @param principal authenticated principal
     * @return issued refresh credential
     */
    IssuedRefreshToken createSession(AuthenticatedPrincipal principal);
}
