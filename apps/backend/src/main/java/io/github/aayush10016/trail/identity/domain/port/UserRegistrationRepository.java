package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.RegisteredUser;

/**
 * Outbound port for storing newly registered users.
 */
public interface UserRegistrationRepository {

    /**
     * Stores a user when the normalized email is still available.
     *
     * @param user user to persist
     * @return storage result without exposing details to clients
     */
    StoreResult store(RegisteredUser user);

    /**
     * Persistence result for normalized-email uniqueness.
     */
    enum StoreResult {
        /**
         * User was persisted.
         */
        CREATED,

        /**
         * Normalized email already exists.
         */
        EMAIL_ALREADY_REGISTERED
    }
}
