package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded solved Problem Session data used to build one canonical embedding document.
 */
public record SolvedProblemEmbeddingContext(
        UUID userId,
        UUID problemSessionId,
        String title,
        String description,
        String localNote,
        String finalSolution,
        String generatedSummary,
        List<String> technologies,
        List<String> frameworks,
        List<String> errorPhrases,
        String problemType,
        List<ProblemSessionEnrichmentResource> resources) {

    /**
     * Creates validated solved-session embedding context.
     */
    public SolvedProblemEmbeddingContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        finalSolution = requireText(finalSolution, "finalSolution");
        technologies = List.copyOf(Objects.requireNonNull(technologies, "technologies"));
        frameworks = List.copyOf(Objects.requireNonNull(frameworks, "frameworks"));
        errorPhrases = List.copyOf(Objects.requireNonNull(errorPhrases, "errorPhrases"));
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
