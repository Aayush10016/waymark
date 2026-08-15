package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationRecord;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationStatus;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationType;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentProjection;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentPromptCatalog;
import io.github.aayush10016.trail.intelligence.domain.model.PromptIdentity;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class JdbcAIDerivationRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Instant NOW = Instant.parse("2026-07-18T09:30:00Z");
    private static final String SCHEMA_HASH = "0".repeat(64);
    private static final String FINGERPRINT = "1".repeat(64);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_ai_derivation_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcAIDerivationRepository repository;

    @BeforeEach
    void migrateFromEmptyDatabase() {
        var flyway =
                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();

        flyway.clean();
        flyway.migrate();

        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcAIDerivationRepository(jdbcTemplate, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void persistsSuccessfulDerivationAndProjectionInOneTransaction() throws SQLException {
        var userId = insertUser("ai-atomic-success@example.com");
        var problemSessionId = UUID.randomUUID();
        insertProblemSession(userId, problemSessionId, "SOLVED");

        repository.saveSuccessfulEnrichment(successfulDerivation(userId, problemSessionId, FINGERPRINT), projection(userId, problemSessionId));

        assertThat(derivationCount(problemSessionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select generated_summary from problem_sessions where id = ?",
                        String.class,
                        problemSessionId))
                .isEqualTo("Spring Security CORS fixed by allowing localhost.");
        assertThat(jdbcTemplate.queryForObject(
                        "select technologies[1] from problem_sessions where id = ?",
                        String.class,
                        problemSessionId))
                .isEqualTo("Spring Security");
        assertThat(jdbcTemplate.queryForObject(
                        "select prompt_state from problem_sessions where id = ?",
                        String.class,
                        problemSessionId))
                .isEqualTo("ENRICHED");
    }

    @Test
    void rollsBackDerivationWhenProjectionCannotBeApplied() throws SQLException {
        var userId = insertUser("ai-atomic-rollback@example.com");
        var problemSessionId = UUID.randomUUID();
        insertProblemSession(userId, problemSessionId, "CANDIDATE");

        assertThatThrownBy(() -> repository.saveSuccessfulEnrichment(
                        successfulDerivation(userId, problemSessionId, "2".repeat(64)),
                        projection(userId, problemSessionId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(derivationCount(problemSessionId)).isZero();
    }

    private AIDerivationRecord successfulDerivation(UUID userId, UUID problemSessionId, String fingerprint) {
        return new AIDerivationRecord(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                AIDerivationType.PROBLEM_SESSION_ENRICHMENT,
                "mock",
                "trail-mock-enrichment",
                new PromptIdentity(
                        ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY,
                        ProblemSessionEnrichmentPromptCatalog.VERSION,
                        ProblemSessionEnrichmentPromptCatalog.HASH),
                SCHEMA_HASH,
                "ai-redaction-v1",
                fingerprint,
                AIDerivationStatus.SUCCEEDED,
                null,
                "Spring Security CORS fixed by allowing localhost.",
                List.of("Spring Security"),
                List.of(),
                List.of("CORS"),
                "configuration",
                new AIUsageMetadata(1, 1, 2),
                AICostMetadata.zeroUsd(),
                NOW,
                NOW);
    }

    private static ProblemSessionEnrichmentProjection projection(UUID userId, UUID problemSessionId) {
        return new ProblemSessionEnrichmentProjection(
                userId,
                problemSessionId,
                "Spring Security CORS fixed by allowing localhost.",
                List.of("Spring Security"),
                List.of(),
                List.of("CORS"),
                "configuration",
                "Spring Security CORS fixed by allowing localhost");
    }

    private UUID insertUser(String email) throws SQLException {
        var userId = UUID.randomUUID();
        try (var connection = connection();
                var statement =
                        connection.prepareStatement(
                                """
                                insert into users (
                                    id,
                                    email_normalized,
                                    email_display,
                                    password_hash,
                                    password_hash_algorithm,
                                    password_hash_parameters,
                                    password_hash_version,
                                    status
                                ) values (?, ?, ?, ?, 'ARGON2ID', ?, 1, 'ACTIVE')
                                """)) {
            statement.setObject(1, userId);
            statement.setString(2, email);
            statement.setString(3, email);
            statement.setString(4, "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash");
            statement.setString(5, "m=65536,t=3,p=2");
            statement.executeUpdate();
        }
        return userId;
    }

    private void insertProblemSession(UUID userId, UUID problemSessionId, String status) throws SQLException {
        try (var connection = connection();
                var statement =
                        connection.prepareStatement(
                                """
                                insert into problem_sessions (
                                    id,
                                    user_id,
                                    status,
                                    source,
                                    title,
                                    final_solution,
                                    final_solution_author,
                                    solution_authored_at,
                                    started_at,
                                    search_document,
                                    created_at,
                                    updated_at
                                ) values (
                                    ?, ?, ?, 'MANUAL', 'Spring Security CORS not working',
                                    ?, ?, ?::timestamptz,
                                    '2026-07-13T08:00:00Z'::timestamptz,
                                    'Spring Security CORS not working',
                                    '2026-07-13T08:00:00Z'::timestamptz,
                                    '2026-07-13T09:00:00Z'::timestamptz
                                )
                                """)) {
            var solved = "SOLVED".equals(status);
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.setString(3, status);
            statement.setString(4, solved ? "Allowed localhost in CorsConfiguration." : null);
            statement.setString(5, solved ? "USER" : null);
            statement.setString(6, solved ? "2026-07-13T09:00:00Z" : null);
            statement.executeUpdate();
        }
    }

    private int derivationCount(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from ai_derivations where problem_session_id = ?", Integer.class, problemSessionId);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
