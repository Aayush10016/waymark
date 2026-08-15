package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;

/**
 * Outbound port for short-lived access-token issuance.
 */
public interface AccessTokenIssuer {

    /**
     * Issues an access token for an authenticated principal.
     *
     * @param principal authenticated principal
     * @return issued access token
     */
    IssuedAccessToken issue(AuthenticatedPrincipal principal);
}
