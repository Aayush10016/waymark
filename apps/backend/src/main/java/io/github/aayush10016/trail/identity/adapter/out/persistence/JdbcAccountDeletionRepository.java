package io.github.aayush10016.trail.identity.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.port.AccountDeletionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for account deletion.
 */
@Repository
public class JdbcAccountDeletionRepository implements AccountDeletionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcAccountDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<PasswordHashRecord> findActivePasswordHash(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select password_hash,
                        password_hash_algorithm,
                        password_hash_parameters,
                        password_hash_version
                    from users
                    where id = ?
                        and status = 'ACTIVE'
                    """,
                    (resultSet, rowNumber) -> new PasswordHashRecord(
                            resultSet.getString("password_hash"),
                            resultSet.getString("password_hash_algorithm"),
                            resultSet.getString("password_hash_parameters"),
                            resultSet.getInt("password_hash_version")),
                    principal.userId()));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void revokeRefreshSessions(AuthenticatedPrincipal principal, Instant revokedAt) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(revokedAt, "revokedAt");
        jdbcTemplate.update(
                """
                update auth_sessions
                set revoked_at = coalesce(revoked_at, ?)
                where user_id = ?
                    and revoked_at is null
                """,
                Timestamp.from(revokedAt),
                principal.userId());
    }

    @Override
    public boolean purgeAccount(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        var deleted = jdbcTemplate.update("delete from users where id = ?", principal.userId());
        return deleted == 1;
    }
}
