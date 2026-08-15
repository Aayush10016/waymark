package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LocalBackendStartupScriptTest {

    private static final String PRIVATE_KEY_CANARY = "PRIVATE_KEY_CANARY_DO_NOT_PRINT";
    private static final String PUBLIC_KEY_CANARY = "PUBLIC_KEY_CANARY_DO_NOT_PRINT";
    private static final String BREVO_KEY_CANARY = "BREVO_KEY_CANARY_DO_NOT_PRINT";

    @TempDir
    private Path tempDir;

    @Test
    void validateOnlyFailsClearlyWhenPrivateKeyIsMissing() throws IOException, InterruptedException {
        assumePowerShellAvailable();
        var trailHome = trailHome();
        createSecret(trailHome, "jwt-public.pem", publicPem());
        createSecret(trailHome, "brevo-api-key.txt", BREVO_KEY_CANARY);
        createLocalConfig(trailHome, "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=sender@example.test");

        var result = runScript(trailHome, "brevo");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Missing required local secret file for JWT private key");
        assertThat(result.output()).doesNotContain(PUBLIC_KEY_CANARY, BREVO_KEY_CANARY);
    }

    @Test
    void validateOnlyFailsClearlyWhenPublicKeyIsMissing() throws IOException, InterruptedException {
        assumePowerShellAvailable();
        var trailHome = trailHome();
        createSecret(trailHome, "jwt-private.pem", privatePem());
        createSecret(trailHome, "brevo-api-key.txt", BREVO_KEY_CANARY);
        createLocalConfig(trailHome, "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=sender@example.test");

        var result = runScript(trailHome, "brevo");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Missing required local secret file for JWT public key");
        assertThat(result.output()).doesNotContain(PRIVATE_KEY_CANARY, BREVO_KEY_CANARY);
    }

    @Test
    void validateOnlyFailsClearlyWhenBrevoKeyIsMissingInBrevoMode() throws IOException, InterruptedException {
        assumePowerShellAvailable();
        var trailHome = trailHome();
        createSecret(trailHome, "jwt-private.pem", privatePem());
        createSecret(trailHome, "jwt-public.pem", publicPem());
        createLocalConfig(trailHome, "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=sender@example.test");

        var result = runScript(trailHome, "brevo");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Missing required local secret file for Brevo API key");
        assertThat(result.output()).doesNotContain(PRIVATE_KEY_CANARY, PUBLIC_KEY_CANARY);
    }

    @Test
    void validateOnlyDoesNotRequireBrevoKeyInLocalEmailMode() throws IOException, InterruptedException {
        assumePowerShellAvailable();
        var trailHome = trailHome();
        createSecret(trailHome, "jwt-private.pem", privatePem());
        createSecret(trailHome, "jwt-public.pem", publicPem());

        var result = runScript(trailHome, "local");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Local backend configuration validation passed.");
        assertThat(result.output()).doesNotContain(PRIVATE_KEY_CANARY, PUBLIC_KEY_CANARY, BREVO_KEY_CANARY);
    }

    @Test
    void validateOnlyDoesNotPrintSecretValuesWhenConfigurationIsValid() throws IOException, InterruptedException {
        assumePowerShellAvailable();
        var trailHome = trailHome();
        createSecret(trailHome, "jwt-private.pem", privatePem());
        createSecret(trailHome, "jwt-public.pem", publicPem());
        createSecret(trailHome, "brevo-api-key.txt", BREVO_KEY_CANARY);
        createLocalConfig(trailHome, "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=sender@example.test");

        var result = runScript(trailHome, "brevo");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Local backend configuration validation passed.");
        assertThat(result.output()).doesNotContain(PRIVATE_KEY_CANARY, PUBLIC_KEY_CANARY, BREVO_KEY_CANARY);
    }

    @Test
    void scriptDelegatesInfrastructureAndMavenStartupWithoutPersistingSecrets() throws IOException {
        var script = Files.readString(startScript(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("start-local-infra.ps1")
                .contains("verify-local-database.ps1")
                .contains("mvnw.cmd")
                .contains("spring-boot:run")
                .contains("SPRING_FLYWAY_ENABLED")
                .doesNotContain("setx")
                .doesNotContain("Set-Content", "Add-Content", "Out-File");
    }

    private static void assumePowerShellAvailable() {
        assumeTrue(isWindows(), "PowerShell script execution contract is Windows-specific");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private Path trailHome() throws IOException {
        var trailHome = tempDir.resolve(".trail");
        Files.createDirectories(trailHome.resolve("secrets"));
        Files.createDirectories(trailHome.resolve("config"));
        return trailHome;
    }

    private static void createSecret(Path trailHome, String fileName, String content) throws IOException {
        Files.writeString(trailHome.resolve("secrets").resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private static void createLocalConfig(Path trailHome, String content) throws IOException {
        Files.writeString(trailHome.resolve("config").resolve("local-backend.env"), content, StandardCharsets.UTF_8);
    }

    private static String privatePem() {
        return ("-----BEGIN " + "PRIVATE KEY-----%n%s%n-----END " + "PRIVATE KEY-----").formatted(PRIVATE_KEY_CANARY);
    }

    private static String publicPem() {
        return ("-----BEGIN " + "PUBLIC KEY-----%n%s%n-----END " + "PUBLIC KEY-----").formatted(PUBLIC_KEY_CANARY);
    }

    private static ScriptResult runScript(Path trailHome, String emailProvider) throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                startScript().toString(),
                "-TrailHome",
                trailHome.toString(),
                "-EmailProvider",
                emailProvider,
                "-ValidateOnly"));
        var process = new ProcessBuilder(command)
                .directory(repositoryRoot().toFile())
                .redirectErrorStream(true)
                .start();

        var finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("start-local-backend.ps1 validation timed out");
        }

        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ScriptResult(process.exitValue(), output);
    }

    private static Path startScript() {
        return repositoryRoot().resolve("scripts/dev/start-local-backend.ps1");
    }

    private static Path repositoryRoot() {
        var current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("scripts/dev/start-local-backend.ps1"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate repository root");
        }
        return current;
    }

    private record ScriptResult(int exitCode, String output) {}
}
