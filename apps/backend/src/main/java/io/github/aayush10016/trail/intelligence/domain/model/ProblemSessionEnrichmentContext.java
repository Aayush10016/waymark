package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded Problem Session data reloaded by an enrichment job before AI redaction.
 */
public record ProblemSessionEnrichmentContext(
        UUID userId,
        UUID problemSessionId,
        String title,
        String description,
        String localNote,
        String finalSolution,
        List<ProblemSessionEnrichmentResource> resources) {

    /**
     * Creates validated enrichment context.
     */
    public ProblemSessionEnrichmentContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        finalSolution = requireText(finalSolution, "finalSolution");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    }

    /**
     * Converts context to ordered, named fields for deterministic redaction.
     */
    public Map<String, String> redactionFields() {
        var fields = new java.util.LinkedHashMap<String, String>();
        putIfPresent(fields, "title", title);
        putIfPresent(fields, "description", description);
        putIfPresent(fields, "localNote", localNote);
        fields.put("finalSolution", finalSolution);
        if (!resources.isEmpty()) {
            fields.put("resources", String.join("\n", resources.stream().map(ProblemSessionEnrichmentResource::asText).toList()));
        }
        return Map.copyOf(fields);
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value.strip());
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
