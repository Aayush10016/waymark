package io.github.aayush10016.trail.intelligence.domain.service;

import io.github.aayush10016.trail.intelligence.domain.model.CanonicalEmbeddingDocument;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentResource;
import io.github.aayush10016.trail.intelligence.domain.model.SolvedProblemEmbeddingContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Builds the single canonical document used for solved Problem Session embeddings.
 */
public final class CanonicalEmbeddingDocumentBuilder {

    /**
     * Stable canonical document version.
     */
    public static final String DOCUMENT_VERSION = "solved-session-embedding-document-v1";

    /**
     * Maximum resources summarized into the one embedding document.
     */
    public static final int MAXIMUM_RESOURCES = 16;

    private static final int MAXIMUM_TEXT_LENGTH = 12_000;

    /**
     * Builds one bounded canonical document. This intentionally performs no per-resource provider calls.
     */
    public CanonicalEmbeddingDocument build(SolvedProblemEmbeddingContext context) {
        Objects.requireNonNull(context, "context");
        var builder = new StringBuilder();
        append(builder, "documentVersion", DOCUMENT_VERSION);
        append(builder, "title", context.title());
        append(builder, "description", context.description());
        append(builder, "localNote", context.localNote());
        append(builder, "finalSolution", context.finalSolution());
        append(builder, "generatedSummary", context.generatedSummary());
        appendList(builder, "technologies", context.technologies());
        appendList(builder, "frameworks", context.frameworks());
        appendList(builder, "errorPhrases", context.errorPhrases());
        append(builder, "problemType", context.problemType());
        appendResources(builder, context.resources());
        var text = bound(builder.toString().strip());
        return new CanonicalEmbeddingDocument(DOCUMENT_VERSION, text, sha256(text));
    }

    private static void append(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(key).append(": ").append(value.strip()).append('\n');
    }

    private static void appendList(StringBuilder builder, String key, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        builder.append(key).append(": ").append(String.join(", ", values)).append('\n');
    }

    private static void appendResources(StringBuilder builder, List<ProblemSessionEnrichmentResource> resources) {
        if (resources.isEmpty()) {
            return;
        }
        builder.append("resources:\n");
        resources.stream()
                .limit(MAXIMUM_RESOURCES)
                .map(ProblemSessionEnrichmentResource::asText)
                .forEach(resource -> builder.append("- ").append(resource).append('\n'));
    }

    private static String bound(String value) {
        if (value.length() <= MAXIMUM_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAXIMUM_TEXT_LENGTH);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
