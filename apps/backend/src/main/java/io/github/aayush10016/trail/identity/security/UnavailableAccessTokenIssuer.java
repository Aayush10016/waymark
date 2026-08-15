package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;

/**
 * Access-token issuer used when signing keys are not configured.
 *
 * <p>The backend can still boot for local development and non-authenticated
 * smoke checks, but credential issuance fails closed until a private signing key
 * is supplied through external configuration.
 */
public final class UnavailableAccessTokenIssuer implements AccessTokenIssuer {

    @Override
    public IssuedAccessToken issue(AuthenticatedPrincipal principal) {
        throw new IllegalStateException("JWT access-token signing key is not configured");
    }
}
