package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated AI-derived fields projected onto a Problem Session.
 */
public record ProblemSessionEnrichmentProjection(
        UUID userId,
        UUID problemSessionId,
        String generatedSummary,
        List<String> technologies,
        List<String> frameworks,
        List<String> errorPhrases,
        String problemType,
        String searchDocument) {

    private static final int MAX_SUMMARY_LENGTH = 4000;
    private static final int MAX_PROBLEM_TYPE_LENGTH = 64;
    private static final int MAX_ARRAY_SIZE = 32;
    private static final int MAX_ARRAY_VALUE_LENGTH = 240;
    private static final int MAX_SEARCH_DOCUMENT_LENGTH = 16_000;

    /**
     * Creates validated projected enrichment fields.
     */
    public ProblemSessionEnrichmentProjection {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        generatedSummary = requireBounded(generatedSummary, "generatedSummary", MAX_SUMMARY_LENGTH);
        technologies = boundedList(technologies, "technologies");
        frameworks = boundedList(frameworks, "frameworks");
        errorPhrases = boundedList(errorPhrases, "errorPhrases");
        problemType = requireBounded(problemType, "problemType", MAX_PROBLEM_TYPE_LENGTH);
        searchDocument = requireBounded(searchDocument, "searchDocument", MAX_SEARCH_DOCUMENT_LENGTH);
    }

    private static List<String> boundedList(List<String> values, String fieldName) {
        var copied = List.copyOf(Objects.requireNonNull(values, fieldName));
        if (copied.size() > MAX_ARRAY_SIZE) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        copied.forEach(value -> requireBounded(value, fieldName + " value", MAX_ARRAY_VALUE_LENGTH));
        return copied;
    }

    private static String requireBounded(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var sanitized = value.strip();
        if (sanitized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded");
        }
        return sanitized;
    }
}
