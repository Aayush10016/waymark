package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvent;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventOutcome;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventProvenance;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationEventRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for recommendation lifecycle events.
 */
@Repository
public class JdbcRecommendationEventRepository implements RecommendationEventRepository {

    private static final TypeReference<List<Map<String, Object>>> JSON_ARRAY_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates the repository.
     */
    public JdbcRecommendationEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public RecommendationEventWriteResult insertIfAbsent(RecommendationEvent event) {
        Objects.requireNonNull(event, "event");
        var inserted = jdbcTemplate.query(
                """
                insert into recommendation_events (
                    id,
                    user_id,
                    recommendation_id,
                    current_problem_session_id,
                    recommended_problem_session_id,
                    outcome,
                    retrieved_problem_sessions,
                    evidence,
                    embedding_generation_key,
                    embedding_generation_version,
                    embedding_provider,
                    embedding_model,
                    embedding_input_fingerprint,
                    algorithm_version,
                    created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id, recommendation_id, outcome) do nothing
                returning id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                event.id(),
                event.userId(),
                event.recommendationId(),
                event.currentProblemSessionId(),
                event.recommendedProblemSessionId(),
                event.outcome().name(),
                toRetrievedJson(event),
                toEvidenceJson(event),
                event.embeddingProvenance().generationKey(),
                event.embeddingProvenance().generationVersion(),
                event.embeddingProvenance().provider(),
                event.embeddingProvenance().model(),
                event.embeddingProvenance().inputFingerprint(),
                event.algorithmVersion(),
                Timestamp.from(event.createdAt()));
        return inserted.isEmpty() ? RecommendationEventWriteResult.DUPLICATE : RecommendationEventWriteResult.INSERTED;
    }

    @Override
    public Optional<RecommendationEvent> findShown(UUID userId, UUID recommendationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(recommendationId, "recommendationId");
        var events = jdbcTemplate.query(
                """
                select
                    id,
                    user_id,
                    recommendation_id,
                    current_problem_session_id,
                    recommended_problem_session_id,
                    outcome,
                    retrieved_problem_sessions::text as retrieved_problem_sessions_json,
                    evidence::text as evidence_json,
                    embedding_generation_key,
                    embedding_generation_version,
                    embedding_provider,
                    embedding_model,
                    embedding_input_fingerprint,
                    algorithm_version,
                    created_at
                from recommendation_events
                where user_id = ?
                    and recommendation_id = ?
                    and outcome = 'SHOWN'
                limit 1
                """,
                this::mapEvent,
                userId,
                recommendationId);
        return events.stream().findFirst();
    }

    private RecommendationEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new RecommendationEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("recommendation_id", UUID.class),
                rs.getObject("current_problem_session_id", UUID.class),
                rs.getObject("recommended_problem_session_id", UUID.class),
                RecommendationEventOutcome.valueOf(rs.getString("outcome")),
                readRetrievedProblemSessions(rs.getString("retrieved_problem_sessions_json")),
                readEvidence(rs.getString("evidence_json")),
                new RecommendationEventProvenance(
                        rs.getString("embedding_generation_key"),
                        rs.getInt("embedding_generation_version"),
                        rs.getString("embedding_provider"),
                        rs.getString("embedding_model"),
                        rs.getString("embedding_input_fingerprint")),
                rs.getString("algorithm_version"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toRetrievedJson(RecommendationEvent event) {
        var rows = event.retrievedProblemSessionIds().stream()
                .map(id -> Map.of("problemSessionId", id.toString()))
                .toList();
        return writeJson(rows);
    }

    private String toEvidenceJson(RecommendationEvent event) {
        var rows = event.evidence().stream()
                .map(reference -> Map.of(
                        "problemSessionId", reference.problemSessionId().toString(),
                        "artifactId", reference.artifactId().map(UUID::toString).orElse(""),
                        "evidenceCode", reference.evidenceCode().name()))
                .toList();
        return writeJson(rows);
    }

    private List<UUID> readRetrievedProblemSessions(String json) {
        return readJsonArray(json).stream()
                .map(row -> UUID.fromString(Objects.toString(row.get("problemSessionId"))))
                .toList();
    }

    private List<RecommendationEvidenceReference> readEvidence(String json) {
        return readJsonArray(json).stream()
                .map(row -> {
                    var problemSessionId = UUID.fromString(Objects.toString(row.get("problemSessionId")));
                    var artifactId = Objects.toString(row.get("artifactId"), "");
                    var code = SimilarityEvidenceCode.valueOf(Objects.toString(row.get("evidenceCode")));
                    return artifactId.isBlank()
                            ? RecommendationEvidenceReference.session(problemSessionId, code)
                            : RecommendationEvidenceReference.artifact(problemSessionId, UUID.fromString(artifactId), code);
                })
                .toList();
    }

    private List<Map<String, Object>> readJsonArray(String json) {
        try {
            return objectMapper.readValue(json, JSON_ARRAY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored recommendation event JSON is invalid.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Recommendation event JSON serialization failed.", exception);
        }
    }
}
