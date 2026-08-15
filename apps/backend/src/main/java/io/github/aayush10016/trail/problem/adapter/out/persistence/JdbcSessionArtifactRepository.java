package io.github.aayush10016.trail.problem.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifactType;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL persistence adapter for ordered Problem Session artifacts.
 */
@Repository
public class JdbcSessionArtifactRepository implements SessionArtifactRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcSessionArtifactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void insert(AuthenticatedPrincipal principal, SessionArtifact artifact) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(artifact, "artifact");

        jdbcTemplate.update(
                """
                insert into session_artifacts (
                    id,
                    user_id,
                    problem_session_id,
                    position,
                    artifact_type,
                    sanitized_url,
                    canonical_url_hash,
                    hostname,
                    title,
                    query_text,
                    source_capture_event_id,
                    occurred_at,
                    decisive,
                    decisive_source,
                    removed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                artifact.id(),
                principal.userId(),
                artifact.problemSessionId(),
                artifact.position(),
                artifact.artifactType().name(),
                artifact.sanitizedUrl(),
                artifact.sanitizedUrl() == null ? null : sha256(artifact.sanitizedUrl()),
                artifact.hostname(),
                artifact.title(),
                artifact.queryText(),
                artifact.sourceCaptureEventId(),
                timestamp(artifact.occurredAt()),
                artifact.decisive(),
                artifact.decisiveSource(),
                nullableTimestamp(artifact.removedAt()));
    }

    @Override
    public int promoteAssociatedCaptureEvents(AuthenticatedPrincipal principal, UUID problemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        return jdbcTemplate.update(
                """
                insert into session_artifacts (
                    id,
                    user_id,
                    problem_session_id,
                    position,
                    artifact_type,
                    sanitized_url,
                    canonical_url_hash,
                    hostname,
                    title,
                    query_text,
                    source_capture_event_id,
                    occurred_at,
                    decisive,
                    decisive_source
                )
                with existing_position as (
                    select coalesce(max(position), 0) as max_position
                    from session_artifacts
                    where user_id = ?
                        and problem_session_id = ?
                        and removed_at is null
                ),
                eligible_capture_events as (
                    select
                        ce.id,
                        ce.sanitized_url,
                        ce.canonical_url_hash,
                        ce.hostname,
                        ce.title,
                        ce.search_query,
                        ce.resource_type,
                        ce.occurred_at,
                        row_number() over (order by ce.occurred_at asc, ce.id asc) as capture_position
                    from capture_events ce
                    where ce.user_id = ?
                        and ce.candidate_problem_session_id = ?
                        and ce.deleted_at is null
                        and not exists (
                            select 1
                            from session_artifacts sa
                            where sa.user_id = ce.user_id
                                and sa.problem_session_id = ce.candidate_problem_session_id
                                and sa.source_capture_event_id = ce.id
                        )
                    order by ce.occurred_at asc, ce.id asc
                    limit ?
                )
                select
                    gen_random_uuid(),
                    ?,
                    ?,
                    existing_position.max_position + eligible_capture_events.capture_position,
                    case eligible_capture_events.resource_type
                        when 'SEARCH' then 'SEARCH_QUERY'
                        when 'DOCUMENTATION' then 'DOCUMENTATION'
                        when 'REPOSITORY' then 'GITHUB_REPOSITORY'
                        when 'FORUM' then 'DISCUSSION'
                        when 'VIDEO' then 'VIDEO'
                        when 'AI_CHAT' then 'AI_CHAT_LINK'
                        else 'PAGE'
                    end,
                    eligible_capture_events.sanitized_url,
                    eligible_capture_events.canonical_url_hash,
                    eligible_capture_events.hostname,
                    eligible_capture_events.title,
                    case
                        when eligible_capture_events.resource_type = 'SEARCH' then eligible_capture_events.search_query
                        else null
                    end,
                    eligible_capture_events.id,
                    eligible_capture_events.occurred_at,
                    false,
                    null
                from eligible_capture_events
                cross join existing_position
                """,
                principal.userId(),
                problemSessionId,
                principal.userId(),
                problemSessionId,
                SessionArtifactRepository.MAXIMUM_PROMOTED_CAPTURE_ARTIFACTS,
                principal.userId(),
                problemSessionId);
    }

    @Override
    public List<SessionArtifact> findActiveByProblemSession(AuthenticatedPrincipal principal, UUID problemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        return jdbcTemplate.query(
                """
                select
                    id,
                    problem_session_id,
                    position,
                    artifact_type,
                    sanitized_url,
                    hostname,
                    title,
                    query_text,
                    source_capture_event_id,
                    occurred_at,
                    decisive,
                    decisive_source,
                    removed_at
                from session_artifacts
                where user_id = ?
                    and problem_session_id = ?
                    and removed_at is null
                order by position asc, id asc
                """,
                (resultSet, rowNumber) -> mapArtifact(resultSet),
                principal.userId(),
                problemSessionId);
    }

    @Override
    public boolean markRemoved(AuthenticatedPrincipal principal, UUID problemSessionId, UUID artifactId, Instant removedAt) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(removedAt, "removedAt");

        var updated = jdbcTemplate.update(
                """
                update session_artifacts
                set removed_at = ?,
                    updated_at = ?
                where user_id = ?
                    and problem_session_id = ?
                    and id = ?
                    and removed_at is null
                """,
                timestamp(removedAt),
                timestamp(removedAt),
                principal.userId(),
                problemSessionId,
                artifactId);
        return updated == 1;
    }

    private static SessionArtifact mapArtifact(ResultSet resultSet) throws SQLException {
        return new SessionArtifact(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("problem_session_id", UUID.class),
                resultSet.getInt("position"),
                SessionArtifactType.valueOf(resultSet.getString("artifact_type")),
                resultSet.getString("sanitized_url"),
                resultSet.getString("hostname"),
                resultSet.getString("title"),
                resultSet.getString("query_text"),
                resultSet.getObject("source_capture_event_id", UUID.class),
                instant(resultSet.getTimestamp("occurred_at")),
                resultSet.getBoolean("decisive"),
                resultSet.getString("decisive_source"),
                instant(resultSet.getTimestamp("removed_at")));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : timestamp(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
