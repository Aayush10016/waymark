package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.util.Optional;

/**
 * Safe default access-token verifier used when no verification key is configured.
 */
public final class RejectingAccessTokenVerifier implements AccessTokenVerifier {

    @Override
    public Optional<AuthenticatedPrincipal> verify(String token) {
        return Optional.empty();
    }
}
