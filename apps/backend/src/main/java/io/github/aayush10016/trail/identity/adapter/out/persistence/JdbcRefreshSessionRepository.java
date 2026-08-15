package io.github.aayush10016.trail.identity.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import io.github.aayush10016.trail.identity.domain.model.UserStatus;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL adapter for hashed refresh-session lifecycle operations.
 */
@Repository
public class JdbcRefreshSessionRepository implements RefreshSessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the repository.
     */
    public JdbcRefreshSessionRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public void create(StoredRefreshSession session) {
        Objects.requireNonNull(session, "session");
        jdbcTemplate.update(
                """
                insert into auth_sessions (
                    id,
                    session_family_id,
                    user_id,
                    refresh_token_hash,
                    refresh_token_hash_algorithm,
                    refresh_rotation_counter,
                    client_type,
                    created_at,
                    expires_at
                ) values (?, ?, ?, ?, 'SHA-256', ?, 'CHROME_EXTENSION', ?, ?)
                """,
                session.id(),
                session.sessionFamilyId(),
                session.principal().userId(),
                session.tokenHash().bytes(),
                session.rotationCounter(),
                Timestamp.from(session.createdAt()),
                Timestamp.from(session.expiresAt()));
    }

    @Override
    public RotationResult rotate(
            RefreshTokenHash presentedTokenHash, RefreshSessionReplacement replacement, Instant rotatedAt) {
        Objects.requireNonNull(presentedTokenHash, "presentedTokenHash");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(rotatedAt, "rotatedAt");
        return transactionTemplate.execute(status -> rotateTransaction(presentedTokenHash, replacement, rotatedAt));
    }

    @Override
    public void revokeCurrentFamily(RefreshTokenHash presentedTokenHash, Instant revokedAt) {
        Objects.requireNonNull(presentedTokenHash, "presentedTokenHash");
        Objects.requireNonNull(revokedAt, "revokedAt");
        transactionTemplate.executeWithoutResult(status -> {
            var session = findSession(presentedTokenHash);
            if (session != null) {
                revokeFamily(session.sessionFamilyId(), revokedAt);
            }
        });
    }

    @Override
    public void revokeAllFamiliesForUser(AuthenticatedPrincipal principal, Instant revokedAt) {
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

    private RotationResult rotateTransaction(
            RefreshTokenHash presentedTokenHash, RefreshSessionReplacement replacement, Instant rotatedAt) {
        var session = findSession(presentedTokenHash);
        if (session == null) {
            return RotationResult.notActive();
        }
        if (session.reuseDetectedAt() != null) {
            return RotationResult.notActive();
        }
        if (session.revokedAt() != null) {
            markFamilyCompromised(session.sessionFamilyId(), rotatedAt);
            return RotationResult.replayDetected();
        }
        if (!session.expiresAt().isAfter(rotatedAt)
                || session.userStatus() != UserStatus.ACTIVE
                || session.emailVerifiedAt() == null) {
            return RotationResult.notActive();
        }

        var consumed = jdbcTemplate.update(
                """
                update auth_sessions
                set revoked_at = ?,
                    last_used_at = ?
                where id = ?
                    and revoked_at is null
                    and reuse_detected_at is null
                """,
                Timestamp.from(rotatedAt),
                Timestamp.from(rotatedAt),
                session.sessionId());
        if (consumed != 1) {
            markFamilyCompromised(session.sessionFamilyId(), rotatedAt);
            return RotationResult.replayDetected();
        }

        jdbcTemplate.update(
                """
                insert into auth_sessions (
                    id,
                    session_family_id,
                    user_id,
                    refresh_token_hash,
                    refresh_token_hash_algorithm,
                    refresh_rotation_counter,
                    client_type,
                    client_version,
                    created_at,
                    expires_at
                ) values (?, ?, ?, ?, 'SHA-256', ?, 'CHROME_EXTENSION', ?, ?, ?)
                """,
                replacement.sessionId(),
                session.sessionFamilyId(),
                session.principal().userId(),
                replacement.tokenHash().bytes(),
                session.rotationCounter() + 1,
                session.clientVersion(),
                Timestamp.from(rotatedAt),
                Timestamp.from(replacement.expiresAt()));
        return RotationResult.rotated(session.principal());
    }

    private SessionRow findSession(RefreshTokenHash tokenHash) {
        return jdbcTemplate
                .query(
                        """
                        select s.id,
                            s.session_family_id,
                            s.user_id,
                            s.refresh_rotation_counter,
                            s.client_version,
                            s.expires_at,
                            s.revoked_at,
                            s.reuse_detected_at,
                            u.status,
                            u.email_verified_at
                        from auth_sessions s
                        join users u on u.id = s.user_id
                        where s.refresh_token_hash = ?
                        for update of s
                        """,
                        (resultSet, rowNumber) ->
                                new SessionRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("session_family_id", UUID.class),
                                        new AuthenticatedPrincipal(resultSet.getObject("user_id", UUID.class)),
                                        resultSet.getInt("refresh_rotation_counter"),
                                        resultSet.getString("client_version"),
                                        resultSet.getTimestamp("expires_at").toInstant(),
                                        resultSet.getTimestamp("revoked_at") == null
                                                ? null
                                                : resultSet.getTimestamp("revoked_at").toInstant(),
                                        resultSet.getTimestamp("reuse_detected_at") == null
                                                ? null
                                                : resultSet.getTimestamp("reuse_detected_at").toInstant(),
                                        UserStatus.valueOf(resultSet.getString("status")),
                                        resultSet.getTimestamp("email_verified_at") == null
                                                ? null
                                                : resultSet.getTimestamp("email_verified_at").toInstant()),
                        tokenHash.bytes())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void revokeFamily(UUID sessionFamilyId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                update auth_sessions
                set revoked_at = coalesce(revoked_at, ?)
                where session_family_id = ?
                    and revoked_at is null
                """,
                Timestamp.from(revokedAt),
                sessionFamilyId);
    }

    private void markFamilyCompromised(UUID sessionFamilyId, Instant detectedAt) {
        jdbcTemplate.update(
                """
                update auth_sessions
                set reuse_detected_at = coalesce(reuse_detected_at, ?),
                    revoked_at = coalesce(revoked_at, ?)
                where session_family_id = ?
                """,
                Timestamp.from(detectedAt),
                Timestamp.from(detectedAt),
                sessionFamilyId);
    }

    private record SessionRow(
            UUID sessionId,
            UUID sessionFamilyId,
            AuthenticatedPrincipal principal,
            int rotationCounter,
            String clientVersion,
            Instant expiresAt,
            Instant revokedAt,
            Instant reuseDetectedAt,
            UserStatus userStatus,
            Instant emailVerifiedAt) {}
}
