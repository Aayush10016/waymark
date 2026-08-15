package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import io.github.aayush10016.trail.retrieval.domain.model.CurrentProblemRecommendationInput;
import io.github.aayush10016.trail.retrieval.domain.port.CurrentProblemRecommendationInputRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for current Problem Session recommendation input.
 */
@Repository
public class JdbcCurrentProblemRecommendationInputRepository implements CurrentProblemRecommendationInputRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcCurrentProblemRecommendationInputRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<CurrentProblemRecommendationInput> findInput(UUID userId, UUID currentProblemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select
                        ps.id,
                        nullif(btrim(ps.search_document), '') as query_text,
                        ps.technologies,
                        ps.error_phrases,
                        pe.generation_key,
                        pe.embedding::text as embedding_text
                    from problem_sessions ps
                    join problem_embeddings pe
                        on pe.user_id = ps.user_id
                        and pe.problem_session_id = ps.id
                        and pe.active = true
                    join embedding_model_generations emg
                        on emg.generation_key = pe.generation_key
                        and emg.generation_version = pe.generation_version
                        and emg.active = true
                    where ps.user_id = ?
                        and ps.id = ?
                        and ps.status in ('CANDIDATE', 'REVIEWABLE')
                        and ps.deleted_at is null
                    order by pe.created_at desc, pe.id desc
                    limit 1
                    """,
                    (resultSet, rowNumber) -> new CurrentProblemRecommendationInput(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("query_text"),
                            stringArray(resultSet.getArray("technologies")),
                            stringArray(resultSet.getArray("error_phrases")),
                            resultSet.getString("generation_key"),
                            vector(resultSet.getString("embedding_text"))),
                    userId,
                    currentProblemSessionId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static List<String> stringArray(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((String[]) array.getArray()).toList();
    }

    private static List<Double> vector(String value) {
        if (value == null || value.length() < 2) {
            return List.of();
        }
        var body = value.substring(1, value.length() - 1);
        if (body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split(",")).map(Double::parseDouble).toList();
    }
}
