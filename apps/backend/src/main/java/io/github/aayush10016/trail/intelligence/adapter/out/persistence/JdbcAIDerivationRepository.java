package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationRecord;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentProjection;
import io.github.aayush10016.trail.intelligence.domain.port.AIDerivationRepository;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL adapter for AI derivation provenance and Problem Session projections.
 */
@Repository
public class JdbcAIDerivationRepository implements AIDerivationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the repository.
     */
    public JdbcAIDerivationRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public void save(AIDerivationRecord derivation) {
        Objects.requireNonNull(derivation, "derivation");
        transactionTemplate.executeWithoutResult(status -> insertDerivation(derivation));
    }

    @Override
    public void saveSuccessfulEnrichment(AIDerivationRecord derivation, ProblemSessionEnrichmentProjection projection) {
        Objects.requireNonNull(derivation, "derivation");
        Objects.requireNonNull(projection, "projection");
        if (!derivation.userId().equals(projection.userId())
                || !derivation.problemSessionId().equals(projection.problemSessionId())) {
            throw new IllegalArgumentException("projection ownership must match derivation ownership");
        }
        transactionTemplate.executeWithoutResult(status -> {
            insertDerivation(derivation);
            var updated = updateProjection(projection, derivation.createdAt());
            if (updated != 1) {
                throw new IllegalStateException("Problem Session projection update failed");
            }
        });
    }

    private void insertDerivation(AIDerivationRecord derivation) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    insert into ai_derivations (
                        id,
                        user_id,
                        problem_session_id,
                        derivation_type,
                        provider,
                        model,
                        prompt_key,
                        prompt_version,
                        prompt_hash,
                        output_schema_hash,
                        policy_version,
                        input_fingerprint,
                        status,
                        safe_failure_code,
                        generated_summary,
                        technologies,
                        frameworks,
                        error_phrases,
                        problem_type,
                        input_tokens,
                        output_tokens,
                        total_tokens,
                        estimated_cost,
                        cost_currency,
                        completed_at,
                        created_at,
                        updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, derivation.id());
                statement.setObject(2, derivation.userId());
                statement.setObject(3, derivation.problemSessionId());
                statement.setString(4, derivation.derivationType().name());
                statement.setString(5, derivation.provider());
                statement.setString(6, derivation.model());
                statement.setString(7, derivation.promptIdentity().key());
                statement.setInt(8, derivation.promptIdentity().version());
                statement.setString(9, derivation.promptIdentity().hash());
                statement.setString(10, derivation.outputSchemaHash());
                statement.setString(11, derivation.policyVersion());
                statement.setString(12, derivation.inputFingerprint());
                statement.setString(13, derivation.status().name());
                statement.setString(14, derivation.safeFailureCode());
                statement.setString(15, derivation.generatedSummary());
                statement.setArray(16, textArray(connection, derivation.technologies()));
                statement.setArray(17, textArray(connection, derivation.frameworks()));
                statement.setArray(18, textArray(connection, derivation.errorPhrases()));
                statement.setString(19, derivation.problemType());
                statement.setLong(20, derivation.usage().inputTokens());
                statement.setLong(21, derivation.usage().outputTokens());
                statement.setLong(22, derivation.usage().totalTokens());
                statement.setBigDecimal(23, derivation.cost().estimatedCost());
                statement.setString(24, derivation.cost().currency().getCurrencyCode());
                statement.setTimestamp(25, timestamp(derivation.completedAt()));
                statement.setTimestamp(26, timestamp(derivation.createdAt()));
                statement.setTimestamp(27, timestamp(derivation.createdAt()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    private int updateProjection(ProblemSessionEnrichmentProjection projection, Instant updatedAt) {
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    update problem_sessions
                    set generated_summary = ?,
                        technologies = ?,
                        frameworks = ?,
                        error_phrases = ?,
                        problem_type = ?,
                        prompt_state = 'ENRICHED',
                        search_document = ?,
                        updated_at = ?
                    where user_id = ?
                        and id = ?
                        and status = 'SOLVED'
                        and deleted_at is null
                    """)) {
                statement.setString(1, projection.generatedSummary());
                statement.setArray(2, textArray(connection, projection.technologies()));
                statement.setArray(3, textArray(connection, projection.frameworks()));
                statement.setArray(4, textArray(connection, projection.errorPhrases()));
                statement.setString(5, projection.problemType());
                statement.setString(6, projection.searchDocument());
                statement.setTimestamp(7, timestamp(updatedAt));
                statement.setObject(8, projection.userId());
                statement.setObject(9, projection.problemSessionId());
                return statement.executeUpdate();
            }
        });
    }

    private static Array textArray(java.sql.Connection connection, List<String> values) throws java.sql.SQLException {
        return connection.createArrayOf("text", values.toArray(String[]::new));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
