package io.github.aayush10016.trail.identity.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.model.EmailAddress;
import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.model.StoredPasswordResetToken;
import io.github.aayush10016.trail.identity.domain.port.PasswordResetTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL adapter for password-reset tokens.
 */
@Repository
public class JdbcPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private static final String PURPOSE = "RESET_PASSWORD";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the repository.
     */
    public JdbcPasswordResetTokenRepository(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public StoreResult storeForActiveEmail(EmailAddress email, StoredPasswordResetToken token) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(token, "token");
        return transactionTemplate.execute(status -> {
            var account = findActiveAccount(email);
            if (account == null) {
                return StoreResult.noActiveUser();
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
                    account.userId(),
                    PURPOSE,
                    token.tokenHash().bytes(),
                    Timestamp.from(token.expiresAt()),
                    Timestamp.from(token.createdAt()));
            return StoreResult.stored(account.email());
        });
    }

    @Override
    public ConsumeResult consume(EmailActionTokenHash tokenHash, PasswordHashRecord newPasswordHash, Instant consumedAt) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        Objects.requireNonNull(consumedAt, "consumedAt");
        return transactionTemplate.execute(status -> {
            var tokenRow = findActiveToken(tokenHash);
            if (tokenRow == null || !tokenRow.expiresAt().isAfter(consumedAt)) {
                return ConsumeResult.notActive();
            }
            var consumed = jdbcTemplate.update(
                    """
                    update email_action_tokens
                    set consumed_at = ?
                    where id = ?
                        and consumed_at is null
                    """,
                    Timestamp.from(consumedAt),
                    tokenRow.tokenId());
            if (consumed != 1) {
                return ConsumeResult.notActive();
            }
            var updated = jdbcTemplate.update(
                    """
                    update users
                    set password_hash = ?,
                        password_hash_algorithm = ?,
                        password_hash_parameters = ?,
                        password_hash_version = ?,
                        updated_at = ?
                    where id = ?
                        and status = 'ACTIVE'
                    """,
                    newPasswordHash.encodedHash(),
                    newPasswordHash.algorithm(),
                    newPasswordHash.parameters(),
                    newPasswordHash.version(),
                    Timestamp.from(consumedAt),
                    tokenRow.userId());
            if (updated != 1) {
                return ConsumeResult.notActive();
            }
            return ConsumeResult.consumed(new AuthenticatedPrincipal(tokenRow.userId()));
        });
    }

    private AccountRow findActiveAccount(EmailAddress email) {
        return jdbcTemplate
                .query(
                        """
                        select id,
                            email_normalized,
                            email_display
                        from users
                        where email_normalized = ?
                            and status = 'ACTIVE'
                            and email_verified_at is not null
                        for update
                        """,
                        (resultSet, rowNumber) ->
                                new AccountRow(
                                        resultSet.getObject("id", UUID.class),
                                        new EmailAddress(
                                                resultSet.getString("email_display"),
                                                resultSet.getString("email_normalized"))),
                        email.normalized())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private TokenRow findActiveToken(EmailActionTokenHash tokenHash) {
        return jdbcTemplate
                .query(
                        """
                        select eat.id,
                            eat.user_id,
                            eat.expires_at
                        from email_action_tokens eat
                        join users u on u.id = eat.user_id
                        where eat.token_hash = ?
                            and eat.purpose = ?
                            and eat.consumed_at is null
                            and u.status = 'ACTIVE'
                        for update of eat, u
                        """,
                        (resultSet, rowNumber) ->
                                new TokenRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("user_id", UUID.class),
                                        resultSet.getTimestamp("expires_at").toInstant()),
                        tokenHash.bytes(),
                        PURPOSE)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private record AccountRow(UUID userId, EmailAddress email) {}

    private record TokenRow(UUID tokenId, UUID userId, Instant expiresAt) {}
}
