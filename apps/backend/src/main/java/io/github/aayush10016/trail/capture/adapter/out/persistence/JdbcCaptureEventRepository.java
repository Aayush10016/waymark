package io.github.aayush10016.trail.capture.adapter.out.persistence;

import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.capture.domain.port.CaptureEventRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL persistence adapter for sanitized capture events.
 */
@Repository
public class JdbcCaptureEventRepository implements CaptureEventRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcCaptureEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public StoreCaptureEventResult storeIfAbsent(AuthenticatedPrincipal principal, CaptureEventToPersist event) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(event, "event");
        var inserted = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    insert into capture_events (
                        id,
                        user_id,
                        client_event_id,
                        client_sequence,
                        client_instance_id,
                        tab_session_id,
                        parent_client_event_id,
                        source,
                        resource_type,
                        navigation_type,
                        sanitized_url,
                        canonical_url_hash,
                        hostname,
                        title,
                        search_engine,
                        search_query,
                        removed_query_parameters,
                        transition_qualifiers,
                        policy_version,
                        browser_event_time_ms,
                        occurred_at,
                        candidate_problem_session_id,
                        retention_expires_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (user_id, client_event_id) do nothing
                    """);
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, principal.userId());
            statement.setString(3, event.clientEventId());
            setNullableLong(statement, 4, event.clientSequence());
            statement.setString(5, event.clientInstanceId());
            statement.setString(6, event.tabSessionId());
            statement.setString(7, event.parentClientEventId());
            statement.setString(8, event.source());
            statement.setString(9, event.resourceType());
            statement.setString(10, event.navigationType());
            statement.setString(11, event.canonicalUrl());
            statement.setBytes(12, sha256(event.canonicalUrl()));
            statement.setString(13, event.hostname());
            statement.setString(14, event.title());
            statement.setString(15, event.searchEngine());
            statement.setString(16, event.searchQuery());
            statement.setArray(17, connection.createArrayOf("text", event.removedQueryParameters().toArray()));
            statement.setArray(18, connection.createArrayOf("text", event.transitionQualifiers().toArray()));
            statement.setString(19, event.policyVersion());
            setNullableDouble(statement, 20, event.browserEventTimeMilliseconds());
            statement.setTimestamp(21, timestamp(event.occurredAt()));
            statement.setObject(22, event.problemSessionId());
            statement.setTimestamp(23, timestamp(event.retentionExpiresAt()));
            return statement;
        });
        return inserted == 1 ? StoreCaptureEventResult.INSERTED : StoreCaptureEventResult.DUPLICATE;
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private static void setNullableDouble(java.sql.PreparedStatement statement, int index, Double value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DOUBLE);
            return;
        }
        statement.setDouble(index, value);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
