package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import io.github.aayush10016.trail.retrieval.domain.port.RecommendationSuppressionRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for recommendation pair suppression state.
 */
@Repository
public class JdbcRecommendationSuppressionRepository implements RecommendationSuppressionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the suppression repository.
     */
    public JdbcRecommendationSuppressionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean isSuppressed(UUID userId, UUID currentProblemSessionId, UUID recommendedProblemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                    from recommendation_events
                    where user_id = ?
                        and current_problem_session_id = ?
                        and recommended_problem_session_id = ?
                        and outcome in ('NOT_RELATED', 'DISMISSED')
                )
                """,
                Boolean.class,
                userId,
                currentProblemSessionId,
                recommendedProblemSessionId));
    }
}
