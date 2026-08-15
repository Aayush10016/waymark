package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the executable database compatibility assumptions required before Render deployment.
 */
@Testcontainers
class RenderPostgresCompatibilityContractTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Path COMPATIBILITY_DOC =
            Path.of("..", "..", "docs", "deployment", "render-postgres-compatibility-v1.md").normalize();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_render_compatibility_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Test
    void pinnedPostgresAndPgvectorCapabilitiesMatchRenderContract() throws SQLException {
        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            assertThat(scalar(statement, "select version()")).contains("PostgreSQL 17.");

            statement.execute("create extension if not exists vector");

            assertThat(scalar(statement, "select extversion from pg_extension where extname = 'vector'"))
                    .isEqualTo("0.8.2");
            assertThat(Double.parseDouble(scalar(statement, "select '[0,1,0]'::vector <-> '[0,2,0]'::vector")))
                    .isEqualTo(1.0D);
        }
    }

    @Test
    void compatibilityDocumentCoversAccessPoolingAndBackupControls() throws IOException {
        var content = Files.readString(COMPATIBILITY_DOC);

        assertThat(content)
                .contains("PostgreSQL 17.x")
                .contains("pgvector 0.8.2")
                .contains("internal URL/private database connection")
                .contains("Public/external database access remains disabled or restricted")
                .contains("Connection pooling")
                .contains("Managed automated backups")
                .contains("ENG-100");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }

    private static String scalar(java.sql.Statement statement, String sql) throws SQLException {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
