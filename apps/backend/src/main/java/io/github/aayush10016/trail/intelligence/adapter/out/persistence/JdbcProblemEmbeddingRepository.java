package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingModelGeneration;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemEmbedding;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemEmbeddingRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL pgvector adapter for Problem Session embeddings.
 */
@Repository
public class JdbcProblemEmbeddingRepository implements ProblemEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcProblemEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void save(ProblemEmbedding embedding) {
        Objects.requireNonNull(embedding, "embedding");
        jdbcTemplate.update(
                """
                insert into problem_embeddings (
                    id,
                    user_id,
                    problem_session_id,
                    generation_key,
                    generation_version,
                    provider,
                    model,
                    dimensions,
                    input_fingerprint,
                    policy_version,
                    embedding,
                    active,
                    superseded_at,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?)
                """,
                embedding.id(),
                embedding.userId(),
                embedding.problemSessionId(),
                embedding.generationKey(),
                embedding.generationVersion(),
                embedding.provider(),
                embedding.model(),
                embedding.dimensions(),
                embedding.inputFingerprint(),
                embedding.policyVersion(),
                vectorLiteral(embedding.vector()),
                embedding.active(),
                timestamp(embedding.supersededAt()),
                timestamp(embedding.createdAt()),
                timestamp(embedding.createdAt()));
    }

    @Override
    @Transactional
    public void replaceActive(ProblemEmbedding embedding) {
        Objects.requireNonNull(embedding, "embedding");
        jdbcTemplate.update(
                """
                update problem_embeddings
                set active = false,
                    superseded_at = ?,
                    updated_at = ?
                where user_id = ?
                    and problem_session_id = ?
                    and generation_key = ?
                    and generation_version = ?
                    and active = true
                    and input_fingerprint <> ?
                """,
                timestamp(embedding.createdAt()),
                timestamp(embedding.createdAt()),
                embedding.userId(),
                embedding.problemSessionId(),
                embedding.generationKey(),
                embedding.generationVersion(),
                embedding.inputFingerprint());
        if (findActive(
                        embedding.userId(),
                        embedding.problemSessionId(),
                        embedding.generationKey(),
                        embedding.generationVersion())
                .filter(existing -> existing.inputFingerprint().equals(embedding.inputFingerprint()))
                .isEmpty()) {
            save(embedding);
        }
    }

    @Override
    public Optional<ProblemEmbedding> findActive(
            UUID userId, UUID problemSessionId, String generationKey, int generationVersion) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        if (generationKey == null || generationKey.isBlank()) {
            throw new IllegalArgumentException("generationKey must not be blank");
        }
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select
                        id,
                        user_id,
                        problem_session_id,
                        generation_key,
                        generation_version,
                        provider,
                        model,
                        dimensions,
                        input_fingerprint,
                        policy_version,
                        embedding::text as embedding,
                        active,
                        superseded_at,
                        created_at
                    from problem_embeddings
                    where user_id = ?
                        and problem_session_id = ?
                        and generation_key = ?
                        and generation_version = ?
                        and active = true
                    """,
                    (resultSet, rowNumber) -> mapEmbedding(resultSet),
                    userId,
                    problemSessionId,
                    generationKey.strip(),
                    generationVersion));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<EmbeddingModelGeneration> findActiveGeneration(String generationKey) {
        if (generationKey == null || generationKey.isBlank()) {
            throw new IllegalArgumentException("generationKey must not be blank");
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select
                        id,
                        generation_key,
                        generation_version,
                        provider,
                        model,
                        dimensions,
                        active,
                        created_at
                    from embedding_model_generations
                    where generation_key = ?
                        and active = true
                    """,
                    (resultSet, rowNumber) -> new EmbeddingModelGeneration(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("generation_key"),
                            resultSet.getInt("generation_version"),
                            resultSet.getString("provider"),
                            resultSet.getString("model"),
                            resultSet.getInt("dimensions"),
                            resultSet.getBoolean("active"),
                            instant(resultSet.getTimestamp("created_at"))),
                    generationKey.strip()));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static ProblemEmbedding mapEmbedding(ResultSet resultSet) throws SQLException {
        return new ProblemEmbedding(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("problem_session_id", UUID.class),
                resultSet.getString("generation_key"),
                resultSet.getInt("generation_version"),
                resultSet.getString("provider"),
                resultSet.getString("model"),
                resultSet.getInt("dimensions"),
                resultSet.getString("input_fingerprint"),
                resultSet.getString("policy_version"),
                parseVector(resultSet.getString("embedding")),
                resultSet.getBoolean("active"),
                instant(resultSet.getTimestamp("superseded_at")),
                instant(resultSet.getTimestamp("created_at")));
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

    private static List<Double> parseVector(String value) {
        var trimmed = value.strip();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("invalid vector literal");
        }
        var body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split(","))
                .map(String::strip)
                .map(Double::parseDouble)
                .toList();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
