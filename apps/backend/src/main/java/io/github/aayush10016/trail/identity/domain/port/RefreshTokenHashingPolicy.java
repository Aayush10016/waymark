package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;

/**
 * Hashes raw refresh credentials before persistence.
 */
public interface RefreshTokenHashingPolicy {

    /**
     * Hashes a raw refresh token.
     *
     * @param rawToken raw refresh token
     * @return refresh-token hash
     */
    RefreshTokenHash hash(String rawToken);
}
