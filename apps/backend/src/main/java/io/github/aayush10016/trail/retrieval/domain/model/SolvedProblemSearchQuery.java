package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * User-scoped solved Problem Session lexical/error/technology search query.
 */
public record SolvedProblemSearchQuery(
        UUID userId,
        String queryText,
        List<String> technologies,
        List<String> errorPhrases,
        List<String> problemTypes,
        int limit,
        int offset) {

    /**
     * Maximum search results returned by the lexical repository.
     */
    public static final int MAXIMUM_LIMIT = 51;

    /**
     * Creates a first-page query.
     */
    public SolvedProblemSearchQuery(
            UUID userId, String queryText, List<String> technologies, List<String> errorPhrases, int limit) {
        this(userId, queryText, technologies, errorPhrases, List.of(), limit, 0);
    }

    /**
     * Creates a query with explicit offset and no problem-type filter.
     */
    public SolvedProblemSearchQuery(
            UUID userId,
            String queryText,
            List<String> technologies,
            List<String> errorPhrases,
            int limit,
            int offset) {
        this(userId, queryText, technologies, errorPhrases, List.of(), limit, offset);
    }

    /**
     * Creates a validated query.
     */
    public SolvedProblemSearchQuery {
        Objects.requireNonNull(userId, "userId");
        queryText = normalizeOptional(queryText);
        technologies = normalizeList(technologies);
        errorPhrases = normalizeList(errorPhrases);
        problemTypes = normalizeList(problemTypes);
        if (limit <= 0 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAXIMUM_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
    }

    /**
     * Whether lexical text terms are present.
     */
    public boolean hasQueryText() {
        return queryText != null;
    }

    /**
     * Whether a problem-type filter is present.
     */
    public boolean hasProblemTypeFilter() {
        return !problemTypes.isEmpty();
    }

    /**
     * Whether this query should return recent solved sessions rather than matched sessions.
     */
    public boolean isRecentOnly() {
        return queryText == null && technologies.isEmpty() && errorPhrases.isEmpty() && problemTypes.isEmpty();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(SolvedProblemSearchQuery::normalizeOptional)
                .filter(Objects::nonNull)
                .distinct()
                .limit(32)
                .toList();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.length() > 240) {
            return normalized.substring(0, 240);
        }
        return normalized;
    }
}
