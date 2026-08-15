package io.github.aayush10016.trail.intelligence.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentResource;
import io.github.aayush10016.trail.intelligence.domain.model.SolvedProblemEmbeddingContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalEmbeddingDocumentBuilderTest {

    private final CanonicalEmbeddingDocumentBuilder builder = new CanonicalEmbeddingDocumentBuilder();

    @Test
    void buildsStableVersionedFingerprintFromOneBoundedSolvedSessionDocument() {
        var context = context(List.of(new ProblemSessionEnrichmentResource(
                "DOCUMENTATION", "Spring CORS docs", "docs.spring.io", "spring cors")));

        var first = builder.build(context);
        var second = builder.build(context);

        assertThat(first.version()).isEqualTo(CanonicalEmbeddingDocumentBuilder.DOCUMENT_VERSION);
        assertThat(first.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
        assertThat(first.text())
                .contains("finalSolution: Allowed localhost in CorsConfiguration.")
                .contains("technologies: Spring Security")
                .contains("resources:");
    }

    @Test
    void boundsCanonicalDocumentAndResourceCount() {
        var resources = java.util.stream.IntStream.range(0, 40)
                .mapToObj(index -> new ProblemSessionEnrichmentResource(
                        "PAGE", "Resource " + index, "example.com", null))
                .toList();

        var document = builder.build(context(resources));

        assertThat(document.text().length()).isLessThanOrEqualTo(12_000);
        assertThat(document.text().lines().filter(line -> line.startsWith("- ")).count())
                .isEqualTo(CanonicalEmbeddingDocumentBuilder.MAXIMUM_RESOURCES);
    }

    private static SolvedProblemEmbeddingContext context(List<ProblemSessionEnrichmentResource> resources) {
        return new SolvedProblemEmbeddingContext(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                "Spring Security CORS not working",
                "Browser blocked localhost frontend.",
                null,
                "Allowed localhost in CorsConfiguration.",
                "CORS configuration was missing localhost.",
                List.of("Spring Security"),
                List.of("Spring"),
                List.of("cors"),
                "CONFIGURATION",
                resources);
    }
}
