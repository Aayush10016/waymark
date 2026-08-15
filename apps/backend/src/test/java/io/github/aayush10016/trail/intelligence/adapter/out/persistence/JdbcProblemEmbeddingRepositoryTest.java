package io.github.aayush10016.trail.intelligence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemEmbedding;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class JdbcProblemEmbeddingRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Instant NOW = Instant.parse("2026-07-18T09:30:00Z");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_embedding_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcProblemEmbeddingRepository repository;

    @BeforeEach
    void migrateFromEmptyDatabase() throws SQLException {
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

        repository = new JdbcProblemEmbeddingRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void savesAndReadsActiveProblemEmbeddingWithinUserBoundary() throws SQLException {
        var firstUserId = insertUser("embedding-repository-owner@example.com");
        var secondUserId = insertUser("embedding-repository-other@example.com");
        var problemSessionId = UUID.randomUUID();
        insertSolvedProblemSession(firstUserId, problemSessionId);
        var embedding = activeEmbedding(firstUserId, problemSessionId, "7".repeat(64));

        repository.save(embedding);

        assertThat(repository.findActive(firstUserId, problemSessionId, "solved-session", 1))
                .contains(embedding);
        assertThat(repository.findActive(secondUserId, problemSessionId, "solved-session", 1))
                .isEmpty();
    }

    @Test
    void ignoresSupersededEmbeddingWhenReadingActive() throws SQLException {
        var userId = insertUser("embedding-repository-superseded@example.com");
        var problemSessionId = UUID.randomUUID();
        insertSolvedProblemSession(userId, problemSessionId);
        repository.save(new ProblemEmbedding(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                3,
                "8".repeat(64),
                "embedding-policy-v1",
                List.of(0.2, 0.3, 0.4),
                false,
                NOW.plusSeconds(60),
                NOW));

        assertThat(repository.findActive(userId, problemSessionId, "solved-session", 1)).isEmpty();
    }

    private static ProblemEmbedding activeEmbedding(UUID userId, UUID problemSessionId, String fingerprint) {
        return new ProblemEmbedding(
                UUID.fromString("30000000-0000-4000-8000-000000000001"),
                userId,
                problemSessionId,
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                3,
                fingerprint,
                "embedding-policy-v1",
                List.of(0.1, 0.2, 0.3),
                true,
                null,
                NOW);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static UUID insertUser(String email) throws SQLException {
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

    private static void insertSolvedProblemSession(UUID userId, UUID problemSessionId) throws SQLException {
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
                                    search_document
                                ) values (
                                    ?, ?, 'SOLVED', 'MANUAL', 'Spring Security CORS not working',
                                    'Allowed localhost in CorsConfiguration.', 'USER',
                                    '2026-07-13T09:00:00Z'::timestamptz,
                                    '2026-07-13T08:00:00Z'::timestamptz,
                                    'Spring Security CORS not working'
                                )
                                """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }
}
