package io.github.aayush10016.trail.identity.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ExtensionIdentityRouteContractTest {

    private static final Path OPENAPI_CONTRACT = Path.of("..", "..", "contracts", "openapi", "trail-v1.yaml");
    private static final Pattern EXTENSION_ROUTE_PATTERN =
            Pattern.compile("(/(?:registrations|auth-sessions(?:/refresh)?|email-verification-requests|"
                    + "email-verifications|password-reset-requests|password-resets))");

    @Test
    void extensionIdentityRoutesExistInOpenApiContract() throws IOException {
        var openApi = Files.readString(OPENAPI_CONTRACT);
        var expectedRoutes = extensionIdentityRoutes();

        assertThat(expectedRoutes)
                .containsExactlyInAnyOrder(
                        "/registrations",
                        "/auth-sessions",
                        "/auth-sessions/refresh",
                        "/email-verification-requests",
                        "/email-verifications",
                        "/password-reset-requests",
                        "/password-resets");
        assertThat(expectedRoutes)
                .allSatisfy(route -> assertThat(openApi).contains("/v1" + route + ":"));
    }

    private static LinkedHashSet<String> extensionIdentityRoutes() throws IOException {
        var routes = new LinkedHashSet<String>();
        for (String source : Files.readAllLines(
                Path.of("..", "extension", "src", "features", "auth", "onboarding", "auth-onboarding-client.ts"))) {
            addRoutes(routes, source);
        }
        for (String source : Files.readAllLines(
                Path.of("..", "extension", "src", "features", "auth", "api", "auth-refresh-client.ts"))) {
            addRoutes(routes, source);
        }
        return routes;
    }

    private static void addRoutes(LinkedHashSet<String> routes, String source) {
        var matcher = EXTENSION_ROUTE_PATTERN.matcher(source);
        while (matcher.find()) {
            routes.add(matcher.group(1));
        }
    }
}
