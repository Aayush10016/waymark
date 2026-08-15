package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the production backend Docker image contract without building the image during unit tests.
 */
class BackendDockerImageContractTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path BACKEND_POM = Path.of("pom.xml");
    private static final Path ROOT_DOCKERIGNORE = Path.of("..", "..", ".dockerignore").normalize();
    private static final Path IMAGE_DOC =
            Path.of("..", "..", "docs", "deployment", "backend-docker-image-v1.md").normalize();

    @Test
    void dockerfileUsesPinnedMultiStageNonRootRuntime() throws IOException {
        var content = Files.readString(DOCKERFILE);

        assertThat(content)
                .contains("maven:3.9.16-eclipse-temurin-17@sha256:")
                .contains("eclipse-temurin:17.0.17_10-jre-noble@sha256:")
                .contains("AS build")
                .contains("apt-get install -y --no-install-recommends unzip")
                .contains("./mvnw -pl apps/backend -am -DskipTests package")
                .contains("USER 10001:10001")
                .contains("HEALTHCHECK")
                .contains("/actuator/health/readiness")
                .contains("STOPSIGNAL SIGTERM")
                .contains("ENTRYPOINT");
        assertThat(content).doesNotContain("TRAIL_DATABASE_PASSWORD", "TRAIL_AI_OPENAI_API_KEY", "TODO", "FIXME");
    }

    @Test
    void backendPackagingCreatesExecutableSpringBootJar() throws IOException {
        var content = Files.readString(BACKEND_POM);

        assertThat(content)
                .contains("<artifactId>spring-boot-maven-plugin</artifactId>")
                .contains("<goal>repackage</goal>");
    }

    @Test
    void dockerContextExcludesSecretsAndGeneratedArtifacts() throws IOException {
        var content = Files.readString(ROOT_DOCKERIGNORE);

        assertThat(content)
                .contains(".env")
                .contains("**/node_modules")
                .contains("**/target")
                .contains("**/dist")
                .contains("*.log");
    }

    @Test
    void dockerImageDocumentationCoversBuildScanHealthAndDeferredRenderConfiguration() throws IOException {
        var content = Files.readString(IMAGE_DOC);

        assertThat(content)
                .contains("apps/backend/Dockerfile")
                .contains("non-root")
                .contains("Health path")
                .contains("/actuator/health/readiness")
                .contains("graceful shutdown")
                .contains("docker scout quickview")
                .contains("ENG-100");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
