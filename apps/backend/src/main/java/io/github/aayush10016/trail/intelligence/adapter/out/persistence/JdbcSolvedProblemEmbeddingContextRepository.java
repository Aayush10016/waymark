package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentResource;
import io.github.aayush10016.trail.intelligence.domain.model.SolvedProblemEmbeddingContext;
import io.github.aayush10016.trail.intelligence.domain.port.SolvedProblemEmbeddingContextRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for bounded solved-session embedding context.
 */
@Repository
public class JdbcSolvedProblemEmbeddingContextRepository implements SolvedProblemEmbeddingContextRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcSolvedProblemEmbeddingContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<SolvedProblemEmbeddingContext> findSolvedContext(UUID userId, UUID problemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select
                        user_id,
                        id,
                        title,
                        description,
                        local_note,
                        final_solution,
                        generated_summary,
                        technologies,
                        frameworks,
                        error_phrases,
                        problem_type
                    from problem_sessions
                    where user_id = ?
                        and id = ?
                        and status = 'SOLVED'
                        and deleted_at is null
                    """,
                    (resultSet, rowNumber) -> new SolvedProblemEmbeddingContext(
                            resultSet.getObject("user_id", UUID.class),
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("title"),
                            resultSet.getString("description"),
                            resultSet.getString("local_note"),
                            resultSet.getString("final_solution"),
                            resultSet.getString("generated_summary"),
                            textArray(resultSet, "technologies"),
                            textArray(resultSet, "frameworks"),
                            textArray(resultSet, "error_phrases"),
                            resultSet.getString("problem_type"),
                            resources(userId, problemSessionId)),
                    userId,
                    problemSessionId));
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

    private static List<String> textArray(ResultSet resultSet, String columnName) throws SQLException {
        var array = resultSet.getArray(columnName);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((String[]) array.getArray()).toList();
    }
}
