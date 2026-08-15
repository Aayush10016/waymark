package io.github.aayush10016.trail.retrieval.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic solved Problem Session retrieval result.
 */
public record SolvedProblemSearchResult(
        UUID problemSessionId,
        String title,
        String finalSolution,
        String generatedSummary,
        List<String> technologies,
        List<String> errorPhrases,
        String problemType,
        double score,
        List<SearchEvidenceCode> evidenceCodes,
        Instant solvedAt,
        Integer durationSeconds,
        Instant updatedAt) {

    /**
     * Creates a validated search result.
     */
    public SolvedProblemSearchResult {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        finalSolution = requireText(finalSolution, "finalSolution");
        technologies = List.copyOf(Objects.requireNonNull(technologies, "technologies"));
        errorPhrases = List.copyOf(Objects.requireNonNull(errorPhrases, "errorPhrases"));
        evidenceCodes = List.copyOf(Objects.requireNonNull(evidenceCodes, "evidenceCodes"));
        if (!Double.isFinite(score) || score < 0) {
            throw new IllegalArgumentException("score must be non-negative and finite");
        }
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be non-negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Creates a search result without solved-time metadata.
     */
    public SolvedProblemSearchResult(
            UUID problemSessionId,
            String title,
            String finalSolution,
            String generatedSummary,
            List<String> technologies,
            List<String> errorPhrases,
            String problemType,
            double score,
            List<SearchEvidenceCode> evidenceCodes,
            Instant updatedAt) {
        this(
                problemSessionId,
                title,
                finalSolution,
                generatedSummary,
                technologies,
                errorPhrases,
                problemType,
                score,
                evidenceCodes,
                null,
                null,
                updatedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
