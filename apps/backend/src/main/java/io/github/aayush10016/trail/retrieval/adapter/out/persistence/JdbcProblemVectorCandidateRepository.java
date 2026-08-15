package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery;
import io.github.aayush10016.trail.retrieval.domain.port.ProblemVectorCandidateRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL pgvector adapter for exact active-generation candidate queries.
 */
@Repository
public class JdbcProblemVectorCandidateRepository implements ProblemVectorCandidateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Duration defaultQueryTimeout;

    /**
     * Creates the repository.
     */
    public JdbcProblemVectorCandidateRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${trail.retrieval.vector-query.timeout:2s}") Duration defaultQueryTimeout) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.defaultQueryTimeout = requirePositiveTimeout(defaultQueryTimeout);
    }

    @Override
    public List<ProblemVectorCandidate> findCandidates(ProblemVectorCandidateQuery query) {
        Objects.requireNonNull(query, "query");
        PreparedStatementCreator statementCreator = connection -> {
            var statement = connection.prepareStatement(sql());
            statement.setQueryTimeout(queryTimeoutSeconds(query.queryTimeout()));
            statement.setString(1, vectorLiteral(query.vector()));
            statement.setObject(2, query.userId());
            statement.setString(3, query.generationKey());
            statement.setString(4, vectorLiteral(query.vector()));
            statement.setInt(5, query.limit());
            return statement;
        };
        return jdbcTemplate.execute(statementCreator, preparedStatement -> {
            try (var resultSet = preparedStatement.executeQuery()) {
                var candidates = new ArrayList<ProblemVectorCandidate>();
                while (resultSet.next()) {
                    candidates.add(mapCandidate(resultSet));
                }
                return List.copyOf(candidates);
            }
        });
    }

    /**
     * Returns the default timeout supplied by configuration.
     */
    public Duration defaultQueryTimeout() {
        return defaultQueryTimeout;
    }

    private static String sql() {
        return """
                select
                    pe.problem_session_id,
                    pe.generation_key,
                    pe.generation_version,
                    pe.provider,
                    pe.model,
                    pe.input_fingerprint,
                    pe.embedding <=> ?::vector as distance
                from problem_embeddings pe
                join embedding_model_generations emg
                    on emg.generation_key = pe.generation_key
                    and emg.generation_version = pe.generation_version
                    and emg.active = true
                join problem_sessions ps
                    on ps.user_id = pe.user_id
                    and ps.id = pe.problem_session_id
                where pe.user_id = ?
                    and pe.generation_key = ?
                    and pe.active = true
                    and ps.status = 'SOLVED'
                    and ps.deleted_at is null
                    and vector_dims(pe.embedding) = vector_dims(?::vector)
                order by distance asc, ps.updated_at desc, pe.problem_session_id asc
                limit ?
                """;
    }

    private static ProblemVectorCandidate mapCandidate(ResultSet resultSet) throws SQLException {
        return new ProblemVectorCandidate(
                resultSet.getObject("problem_session_id", UUID.class),
                resultSet.getString("generation_key"),
                resultSet.getInt("generation_version"),
                resultSet.getString("provider"),
                resultSet.getString("model"),
                resultSet.getString("input_fingerprint"),
                resultSet.getDouble("distance"));
    }

    private static String vectorLiteral(List<Double> vector) {
        return vector.stream()
                .map(value -> {
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("vector values must be finite");
                    }
                    return Double.toString(value);
                })
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static int queryTimeoutSeconds(Duration timeout) {
        var effectiveTimeout = requirePositiveTimeout(timeout);
        var seconds = Math.ceil(effectiveTimeout.toMillis() / 1000.0);
        return Math.max(1, (int) seconds);
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return timeout;
    }
}
