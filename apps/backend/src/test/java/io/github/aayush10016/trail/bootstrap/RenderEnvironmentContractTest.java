package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the repository-side Render environment contract created for ENG-100.
 */
class RenderEnvironmentContractTest {

    private static final Path CONTRACT =
            Path.of("..", "..", "infra", "environments", "render-v1.1-contract.md").normalize();
    private static final Path ENV_EXAMPLE =
            Path.of("..", "..", "infra", "environments", "render-staging.env.example").normalize();

    @Test
    void renderContractDocumentsWebServiceDatabaseHealthEnvironmentSecretsAndRegion() throws IOException {
        var content = Files.readString(CONTRACT);

        assertThat(content)
                .contains("Region contract")
                .contains("Web service contract")
                .contains("Database contract")
                .contains("/actuator/health/readiness")
                .contains("Runtime environment variables")
                .contains("Secret rules")
                .contains("No Kubernetes or provider lock in application code")
                .contains("TRAIL_DATABASE_URL")
                .contains("TRAIL_DATABASE_PASSWORD")
                .contains("TRAIL_AI_PROVIDER")
                .contains("TRAIL_EMBEDDING_PROVIDER");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }

    @Test
    void stagingExampleUsesPlaceholdersAndDoesNotCommitSecrets() throws IOException {
        var content = Files.readString(ENV_EXAMPLE);

        assertThat(content)
                .contains("TRAIL_DATABASE_URL=[render-internal-postgres-jdbc-url]")
                .contains("TRAIL_DATABASE_PASSWORD=[render-secret]")
                .contains("TRAIL_AI_PROVIDER=mock")
                .contains("TRAIL_EMBEDDING_PROVIDER=mock");
        assertThat(content)
                .doesNotContain(
                        "jdbc:postgresql://",
                        "sk-",
                        "-----BEGIN",
                        "-----END",
                        "render.com/d/",
                        "TODO",
                        "FIXME");
    }
}
