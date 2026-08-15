package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.util.Optional;

/**
 * Verifies a bearer access token and returns the authenticated principal when valid.
 */
public interface AccessTokenVerifier {

    /**
     * Verifies an access token.
     *
     * @param token raw bearer token without the {@code Bearer} prefix
     * @return authenticated principal when the token is valid
     */
    Optional<AuthenticatedPrincipal> verify(String token);
}
