package io.github.aayush10016.trail.problem.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionDeletionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL deletion adapter for user-owned Problem Sessions.
 */
@Repository
public class JdbcProblemSessionDeletionRepository implements ProblemSessionDeletionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcProblemSessionDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean delete(AuthenticatedPrincipal principal, UUID problemSessionId, Instant deletedAt) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(deletedAt, "deletedAt");

        if (!ownedActiveProblemSessionExists(principal, problemSessionId)) {
            return false;
        }

        jdbcTemplate.update(
                """
                update capture_events
                set deleted_at = coalesce(deleted_at, ?),
                    candidate_problem_session_id = null
                where user_id = ?
                    and candidate_problem_session_id = ?
                """,
                Timestamp.from(deletedAt),
                principal.userId(),
                problemSessionId);
        jdbcTemplate.update(
                """
                delete from background_jobs
                where user_id = ?
                    and aggregate_type = 'PROBLEM_SESSION'
                    and aggregate_id = ?
                """,
                principal.userId(),
                problemSessionId);
        jdbcTemplate.update(
                """
                delete from problem_sessions
                where user_id = ?
                    and id = ?
                    and deleted_at is null
                """,
                principal.userId(),
                problemSessionId);
        return true;
    }

    private boolean ownedActiveProblemSessionExists(AuthenticatedPrincipal principal, UUID problemSessionId) {
        var count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from problem_sessions
                where user_id = ?
                    and id = ?
                    and deleted_at is null
                """,
                Integer.class,
                principal.userId(),
                problemSessionId);
        return count != null && count == 1;
    }
}
