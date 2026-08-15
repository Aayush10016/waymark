package io.github.aayush10016.trail.problem.adapter.out.persistence;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.FinalSolution;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL persistence adapter for user-owned Problem Sessions.
 */
@Repository
public class JdbcProblemSessionRepository implements ProblemSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcProblemSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(snapshot, "snapshot");

        var solution = snapshot.session().finalSolution();
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    version,
                    title,
                    description,
                    local_note,
                    final_solution,
                    final_solution_author,
                    solution_authored_at,
                    started_at,
                    ended_at,
                    duration_seconds,
                    search_document,
                    deleted_at,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                principal.userId(),
                snapshot.session().status().name(),
                snapshot.source().name(),
                snapshot.version(),
                snapshot.title(),
                snapshot.description(),
                snapshot.localNote(),
                solution.map(FinalSolution::text).orElse(null),
                solution.map(FinalSolution::author).map(Enum::name).orElse(null),
                solution.map(FinalSolution::authoredAt).map(Timestamp::from).orElse(null),
                timestamp(snapshot.startedAt()),
                nullableTimestamp(snapshot.endedAt()),
                durationSeconds(snapshot),
                searchDocument(snapshot),
                nullableTimestamp(snapshot.deletedAt()),
                timestamp(snapshot.createdAt()),
                timestamp(snapshot.updatedAt()));
    }

    @Override
    public Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select
                        id,
                        status,
                        source,
                        version,
                        title,
                        description,
                        local_note,
                        final_solution,
                        final_solution_author,
                        solution_authored_at,
                        started_at,
                        ended_at,
                        created_at,
                        updated_at,
                        deleted_at
                    from problem_sessions
                    where user_id = ?
                        and id = ?
                        and deleted_at is null
                    """,
                    (resultSet, rowNumber) -> mapSnapshot(resultSet),
                    principal.userId(),
                    problemSessionId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(snapshot, "snapshot");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }

        var solution = snapshot.session().finalSolution();
        var updated = jdbcTemplate.update(
                """
                update problem_sessions
                set status = ?,
                    source = ?,
                    version = version + 1,
                    title = ?,
                    description = ?,
                    local_note = ?,
                    final_solution = ?,
                    final_solution_author = ?,
                    solution_authored_at = ?,
                    started_at = ?,
                    ended_at = ?,
                    duration_seconds = ?,
                    search_document = ?,
                    deleted_at = ?,
                    updated_at = ?
                where user_id = ?
                    and id = ?
                    and version = ?
                """,
                snapshot.session().status().name(),
                snapshot.source().name(),
                snapshot.title(),
                snapshot.description(),
                snapshot.localNote(),
                solution.map(FinalSolution::text).orElse(null),
                solution.map(FinalSolution::author).map(Enum::name).orElse(null),
                solution.map(FinalSolution::authoredAt).map(Timestamp::from).orElse(null),
                timestamp(snapshot.startedAt()),
                nullableTimestamp(snapshot.endedAt()),
                durationSeconds(snapshot),
                searchDocument(snapshot),
                nullableTimestamp(snapshot.deletedAt()),
                timestamp(snapshot.updatedAt()),
                principal.userId(),
                snapshot.id(),
                expectedVersion);
        return updated == 1;
    }

    private static ProblemSessionSnapshot mapSnapshot(ResultSet resultSet) throws SQLException {
        var status = ProblemSessionStatus.valueOf(resultSet.getString("status"));
        var finalSolutionText = resultSet.getString("final_solution");
        var finalSolutionAuthoredAt = instant(resultSet.getTimestamp("solution_authored_at"));
        var finalSolution = status == ProblemSessionStatus.SOLVED
                ? FinalSolution.userAuthored(finalSolutionText, finalSolutionAuthoredAt)
                : null;
        return new ProblemSessionSnapshot(
                resultSet.getObject("id", UUID.class),
                ProblemSessionSource.valueOf(resultSet.getString("source")),
                ProblemSession.rehydrate(status, finalSolution),
                resultSet.getLong("version"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("local_note"),
                instant(resultSet.getTimestamp("started_at")),
                instant(resultSet.getTimestamp("ended_at")),
                instant(resultSet.getTimestamp("created_at")),
                instant(resultSet.getTimestamp("updated_at")),
                instant(resultSet.getTimestamp("deleted_at")));
    }

    private static Integer durationSeconds(ProblemSessionSnapshot snapshot) {
        if (snapshot.endedAt() == null) {
            return null;
        }
        return Math.toIntExact(snapshot.endedAt().getEpochSecond() - snapshot.startedAt().getEpochSecond());
    }

    private static String searchDocument(ProblemSessionSnapshot snapshot) {
        var builder = new StringBuilder();
        appendSearchText(builder, snapshot.title());
        appendSearchText(builder, snapshot.description());
        appendSearchText(builder, snapshot.localNote());
        snapshot.session().finalSolution().map(FinalSolution::text).ifPresent(text -> appendSearchText(builder, text));
        return builder.toString().trim();
    }

    private static void appendSearchText(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value);
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
}
