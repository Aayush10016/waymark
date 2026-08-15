package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentContext;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentResource;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemSessionEnrichmentContextRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for bounded solved Problem Session enrichment context.
 */
@Repository
public class JdbcProblemSessionEnrichmentContextRepository implements ProblemSessionEnrichmentContextRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcProblemSessionEnrichmentContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<ProblemSessionEnrichmentContext> findSolvedContext(UUID userId, UUID problemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        try {
            var context = jdbcTemplate.queryForObject(
                    """
                    select
                        user_id,
                        id,
                        title,
                        description,
                        local_note,
                        final_solution
                    from problem_sessions
                    where user_id = ?
                        and id = ?
                        and status = 'SOLVED'
                        and deleted_at is null
                    """,
                    (resultSet, rowNumber) -> new ProblemSessionEnrichmentContext(
                            resultSet.getObject("user_id", UUID.class),
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("title"),
                            resultSet.getString("description"),
                            resultSet.getString("local_note"),
                            resultSet.getString("final_solution"),
                            resources(userId, problemSessionId)),
                    userId,
                    problemSessionId);
            return Optional.ofNullable(context);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private List<ProblemSessionEnrichmentResource> resources(UUID userId, UUID problemSessionId) {
        return jdbcTemplate.query(
                """
                select artifact_type, title, hostname, query_text
                from session_artifacts
                where user_id = ?
                    and problem_session_id = ?
                    and removed_at is null
                order by position asc, id asc
                limit 32
                """,
                (resultSet, rowNumber) -> mapResource(resultSet),
                userId,
                problemSessionId);
    }

    private static ProblemSessionEnrichmentResource mapResource(ResultSet resultSet) throws SQLException {
        return new ProblemSessionEnrichmentResource(
                resultSet.getString("artifact_type"),
                resultSet.getString("title"),
                resultSet.getString("hostname"),
                resultSet.getString("query_text"));
    }
}
