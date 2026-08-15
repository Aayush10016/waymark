package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.LoginAccount;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for login account lookup and password-hash maintenance.
 */
public interface LoginUserRepository {

    /**
     * Finds an account by normalized email.
     *
     * @param email normalized email value
     * @return matching account when present
     */
    Optional<LoginAccount> findByEmail(EmailAddress email);

    /**
     * Updates a verified account password hash after successful authentication
     * when the current password policy requires rehashing.
     *
     * @param userId immutable user ID
     * @param passwordHash new password hash and metadata
     */
    void updatePasswordHash(UUID userId, PasswordHashRecord passwordHash);
}
