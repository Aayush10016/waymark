package io.github.aayush10016.trail.identity.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.StoredEmailVerificationToken;
import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL adapter for email-verification tokens.
 */
@Repository
public class JdbcEmailVerificationTokenRepository implements EmailVerificationTokenRepository {

    private static final String PURPOSE = "VERIFY_EMAIL";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the repository.
     */
    public JdbcEmailVerificationTokenRepository(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public StoreResult storeForPendingEmail(EmailAddress email, StoredEmailVerificationToken token) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(token, "token");
        return transactionTemplate.execute(status -> {
            var userId = findPendingUserId(email);
            if (userId == null) {
                return StoreResult.NO_PENDING_USER;
            }
            jdbcTemplate.update(
                    """
                    insert into email_action_tokens (
                        id,
                        user_id,
                        purpose,
                        token_hash,
                        token_hash_algorithm,
                        expires_at,
                        created_at
                    ) values (?, ?, ?, ?, 'SHA-256', ?, ?)
                    """,
                    token.id(),
                    userId,
                    PURPOSE,
                    token.tokenHash().bytes(),
                    Timestamp.from(token.expiresAt()),
                    Timestamp.from(token.createdAt()));
            return StoreResult.STORED;
        });
    }

    @Override
    public ConsumeResult consume(EmailActionTokenHash tokenHash, Instant consumedAt) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(consumedAt, "consumedAt");
        return transactionTemplate.execute(status -> {
            var tokenRow = findActiveToken(tokenHash, PURPOSE);
            if (tokenRow == null || !tokenRow.expiresAt().isAfter(consumedAt)) {
                return ConsumeResult.NOT_ACTIVE;
            }
            var updated = jdbcTemplate.update(
                    """
                    update email_action_tokens
                    set consumed_at = ?
                    where id = ?
                        and consumed_at is null
                    """,
                    Timestamp.from(consumedAt),
                    tokenRow.tokenId());
            if (updated != 1) {
                return ConsumeResult.NOT_ACTIVE;
            }
            jdbcTemplate.update(
                    """
                    update users
                    set status = 'ACTIVE',
                        email_verified_at = coalesce(email_verified_at, ?),
                        updated_at = ?
                    where id = ?
                        and status in ('PENDING_VERIFICATION', 'ACTIVE')
                    """,
                    Timestamp.from(consumedAt),
                    Timestamp.from(consumedAt),
                    tokenRow.userId());
            return ConsumeResult.CONSUMED;
        });
    }

    private UUID findPendingUserId(EmailAddress email) {
        return jdbcTemplate
                .query(
                        """
                        select id
                        from users
                        where email_normalized = ?
                            and status = 'PENDING_VERIFICATION'
                        for update
                        """,
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                        email.normalized())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private TokenRow findActiveToken(EmailActionTokenHash tokenHash, String purpose) {
        return jdbcTemplate
                .query(
                        """
                        select id,
                            user_id,
                            expires_at
                        from email_action_tokens
                        where token_hash = ?
                            and purpose = ?
                            and consumed_at is null
                        for update
                        """,
                        (resultSet, rowNumber) ->
                                new TokenRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("user_id", UUID.class),
                                        resultSet.getTimestamp("expires_at").toInstant()),
                        tokenHash.bytes(),
                        purpose)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private record TokenRow(UUID tokenId, UUID userId, Instant expiresAt) {}
}
