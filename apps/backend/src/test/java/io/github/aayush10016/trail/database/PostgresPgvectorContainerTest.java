package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresPgvectorContainerTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Test
    void shouldStartPostgres17WithPgvector082Enabled() throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            var postgresVersion = scalar(statement, "select version()");
            assertThat(postgresVersion).contains("PostgreSQL 17.");

            statement.execute("create extension if not exists vector");
            var pgvectorVersion = scalar(statement, "select extversion from pg_extension where extname = 'vector'");
            assertThat(pgvectorVersion).isEqualTo("0.8.2");

            var distance = scalar(statement, "select '[1,2,3]'::vector <-> '[1,2,4]'::vector");
            assertThat(Double.parseDouble(distance)).isEqualTo(1.0D);
        }
    }

    private static String scalar(java.sql.Statement statement, String sql) throws SQLException {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}

