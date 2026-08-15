package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.retrieval.adapter.out.persistence.JdbcProblemVectorCandidateRepository;
import io.github.aayush10016.trail.retrieval.adapter.out.persistence.JdbcSolvedProblemLexicalSearchRepository;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
class RetrievalLatencyAndQueryPlanBenchmarkTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Duration LEXICAL_P95_TARGET = Duration.ofMillis(300);
    private static final Duration VECTOR_P95_TARGET = Duration.ofMillis(750);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_retrieval_benchmark_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcSolvedProblemLexicalSearchRepository lexicalRepository;
    private JdbcProblemVectorCandidateRepository vectorRepository;
    private UUID largeUserId;

    @BeforeEach
    void migrateAndSeedSkewedUsers() throws SQLException {
        var flyway =
                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();
        flyway.clean();
        flyway.migrate();

        jdbcTemplate = new JdbcTemplate(dataSource());
        lexicalRepository = new JdbcSolvedProblemLexicalSearchRepository(jdbcTemplate);
        vectorRepository = new JdbcProblemVectorCandidateRepository(jdbcTemplate, Duration.ofSeconds(2));

        try (var connection = connection()) {
            largeUserId = insertUser(connection, "retrieval-large@example.com");
            var otherUserId = insertUser(connection, "retrieval-small@example.com");
            for (var index = 0; index < 120; index++) {
                var sessionId = UUID.randomUUID();
                insertSolvedSession(connection, largeUserId, sessionId, index, "Spring Security CORS " + index);
                insertEmbedding(connection, largeUserId, sessionId, index, vector(index));
            }
            for (var index = 0; index < 20; index++) {
                var sessionId = UUID.randomUUID();
                insertSolvedSession(connection, otherUserId, sessionId, index, "Other user CORS " + index);
                insertEmbedding(connection, otherUserId, sessionId, index, vector(index));
            }
        }
    }

    @Test
    void lexicalSearchMeetsP95TargetAndUsesSearchIndexesForSkewedUsers() {
        var samples = new ArrayList<Duration>();
        for (var index = 0; index < 20; index++) {
            samples.add(measure(() -> lexicalRepository.search(new SolvedProblemSearchQuery(
                    largeUserId,
                    "spring cors",
                    List.of("spring security"),
                    List.of("cors"),
                    10))));
        }

        assertThat(p95(samples)).isLessThan(LEXICAL_P95_TARGET);
        assertThat(explainLexicalPlan())
                .containsAnyOf(
                        "idx_problem_sessions_search_vector",
                        "idx_problem_sessions_error_phrases_gin",
                        "idx_problem_sessions_technologies_gin",
                        "idx_problem_sessions_solved_user_updated");
    }

    @Test
    void exactVectorRetrievalMeetsP95TargetUsesUserGenerationIndexAndHasNoHnswIndex() {
        var samples = new ArrayList<Duration>();
        for (var index = 0; index < 20; index++) {
            samples.add(measure(() -> vectorRepository.findCandidates(new ProblemVectorCandidateQuery(
                    largeUserId,
                    "solved-session",
                    List.of(1.0, 0.0, 0.0),
                    10,
                    Duration.ofSeconds(2)))));
        }

        assertThat(p95(samples)).isLessThan(VECTOR_P95_TARGET);
        assertThat(explainVectorPlan())
                .contains("Index Scan")
                .contains("problem_embeddings")
                .contains("user_id")
                .contains("generation_key");
        assertThat(hnswIndexCount()).isZero();
    }

    private String explainLexicalPlan() {
        jdbcTemplate.execute("set enable_seqscan = off");
        return String.join("\n", jdbcTemplate.queryForList(
                """
                explain
                select id
                from problem_sessions
                where user_id = ?::uuid
                    and status = 'SOLVED'
                    and deleted_at is null
                    and (
                        search_vector @@ plainto_tsquery('simple', 'spring cors')
                        or error_phrases && array['cors']::text[]
                        or technologies && array['spring security']::text[]
                    )
                order by updated_at desc, id desc
                limit 10
                """,
                String.class,
                largeUserId.toString()));
    }

    private String explainVectorPlan() {
        jdbcTemplate.execute("set enable_seqscan = off");
        return String.join("\n", jdbcTemplate.queryForList(
                """
                explain
                select pe.problem_session_id
                from problem_embeddings pe
                join embedding_model_generations emg
                    on emg.generation_key = pe.generation_key
                    and emg.generation_version = pe.generation_version
                    and emg.active = true
                join problem_sessions ps
                    on ps.user_id = pe.user_id
                    and ps.id = pe.problem_session_id
                where pe.user_id = ?::uuid
                    and pe.generation_key = 'solved-session'
                    and pe.active = true
                    and ps.status = 'SOLVED'
                    and ps.deleted_at is null
                order by pe.embedding <=> '[1,0,0]'::vector
                limit 10
                """,
                String.class,
                largeUserId.toString()));
    }

    private Integer hnswIndexCount() {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                    and lower(indexdef) like '% using hnsw %'
                """,
                Integer.class);
    }

    private static Duration measure(Runnable operation) {
        var started = System.nanoTime();
        operation.run();
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static Duration p95(List<Duration> samples) {
        var sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
        var index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static PGSimpleDataSource dataSource() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static UUID insertUser(Connection connection, String email) throws SQLException {
        var userId = UUID.randomUUID();
        try (var statement = connection.prepareStatement(
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

    private static void insertSolvedSession(
            Connection connection,
            UUID userId,
            UUID sessionId,
            int index,
            String title)
            throws SQLException {
        try (var statement = connection.prepareStatement(
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
                    ended_at,
                    technologies,
                    error_phrases,
                    problem_type,
                    search_document,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, 'SOLVED', 'MANUAL', ?, 'Allowed localhost in CorsConfiguration.',
                    'USER', '2026-07-20T10:00:00Z'::timestamptz,
                    '2026-07-20T09:00:00Z'::timestamptz,
                    '2026-07-20T10:00:00Z'::timestamptz,
                    array['spring security']::text[], array['cors']::text[],
                    'configuration', ?, '2026-07-20T09:00:00Z'::timestamptz,
                    ?::timestamptz
                )
                """)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, userId);
            statement.setString(3, title);
            statement.setString(4, title + " Spring Security CORS");
            statement.setString(5, "2026-07-20T10:" + "%02d".formatted(index % 60) + ":00Z");
            statement.executeUpdate();
        }
    }

    private static void insertEmbedding(
            Connection connection,
            UUID userId,
            UUID sessionId,
            int index,
            String vector)
            throws SQLException {
        try (var statement = connection.prepareStatement(
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
                    created_at,
                    updated_at
                ) values (
                    ?, ?, ?, 'solved-session', 1, 'mock', 'trail-mock-embedding',
                    3, ?, 'embedding-policy-v1', ?::vector, true,
                    '2026-07-20T10:00:00Z'::timestamptz,
                    '2026-07-20T10:00:00Z'::timestamptz
                )
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, sessionId);
            statement.setString(4, "%064x".formatted(index + 1));
            statement.setString(5, vector);
            statement.executeUpdate();
        }
    }

    private static String vector(int index) {
        var second = Math.min(0.99, index / 200.0);
        var first = 1.0 - second;
        return "[" + first + "," + second + ",0]";
    }
}
