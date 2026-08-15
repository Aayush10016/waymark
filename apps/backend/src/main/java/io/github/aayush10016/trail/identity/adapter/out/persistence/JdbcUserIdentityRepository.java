package io.github.aayush10016.trail.identity.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.LoginAccount;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.RegisteredUser;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.LoginUserRepository;
import io.github.aayush10016.trail.identity.domain.port.UserRegistrationRepository;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for user registration and login account lookup.
 */
@Repository
public class JdbcUserIdentityRepository implements UserRegistrationRepository, LoginUserRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcUserIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public StoreResult store(RegisteredUser user) {
        Objects.requireNonNull(user, "user");
        try {
            jdbcTemplate.update(
                    """
                    insert into users (
                        id,
                        email_normalized,
                        email_display,
                        password_hash,
                        password_hash_algorithm,
                        password_hash_parameters,
                        password_hash_version,
                        status,
                        created_at,
                        updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    user.id(),
                    user.email().normalized(),
                    user.email().display(),
                    user.passwordHash().encodedHash(),
                    user.passwordHash().algorithm(),
                    user.passwordHash().parameters(),
                    user.passwordHash().version(),
                    user.status().name(),
                    Timestamp.from(user.createdAt()),
                    Timestamp.from(user.createdAt()));
            return StoreResult.CREATED;
        } catch (DuplicateKeyException exception) {
            return StoreResult.EMAIL_ALREADY_REGISTERED;
        }
    }

    @Override
    public Optional<LoginAccount> findByEmail(EmailAddress email) {
        Objects.requireNonNull(email, "email");
        return jdbcTemplate
                .query(
                        """
                        select id,
                            email_normalized,
                            email_display,
                            password_hash,
                            password_hash_algorithm,
                            password_hash_parameters,
                            password_hash_version,
                            status,
                            email_verified_at
                        from users
                        where email_normalized = ?
                        """,
                        (resultSet, rowNumber) ->
                                new LoginAccount(
                                        resultSet.getObject("id", UUID.class),
                                        new EmailAddress(
                                                resultSet.getString("email_display"),
                                                resultSet.getString("email_normalized")),
                                        new PasswordHashRecord(
                                                resultSet.getString("password_hash"),
                                                resultSet.getString("password_hash_algorithm"),
                                                resultSet.getString("password_hash_parameters"),
                                                resultSet.getInt("password_hash_version")),
                                        UserStatus.valueOf(resultSet.getString("status")),
                                        resultSet.getTimestamp("email_verified_at") == null
                                                ? null
                                                : resultSet.getTimestamp("email_verified_at").toInstant()),
                        email.normalized())
                .stream()
                .findFirst();
    }

    @Override
    public void updatePasswordHash(UUID userId, PasswordHashRecord passwordHash) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(passwordHash, "passwordHash");
        jdbcTemplate.update(
                """
                update users
                set password_hash = ?,
                    password_hash_algorithm = ?,
                    password_hash_parameters = ?,
                    password_hash_version = ?,
                    updated_at = now()
                where id = ?
                    and status = 'ACTIVE'
                """,
                passwordHash.encodedHash(),
                passwordHash.algorithm(),
                passwordHash.parameters(),
                passwordHash.version(),
                userId);
    }
}
