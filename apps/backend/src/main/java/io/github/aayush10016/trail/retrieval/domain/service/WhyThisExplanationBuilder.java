package io.github.aayush10016.trail.retrieval.domain.service;

import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationExplanation;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationExplanationMessage;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds deterministic recommendation explanations from structured evidence codes.
 */
public class WhyThisExplanationBuilder {

    private static final int MAX_MESSAGES = 3;

    /**
     * Builds a bounded explanation without model-generated rationale or raw scores.
     */
    public RecommendationExplanation build(
            UUID recommendedProblemSessionId,
            String algorithmVersion,
            List<RecommendationEvidenceReference> evidenceReferences) {
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        var messages = Objects.requireNonNull(evidenceReferences, "evidenceReferences").stream()
                .filter(reference -> reference.problemSessionId().equals(recommendedProblemSessionId))
                .sorted(Comparator.comparing((RecommendationEvidenceReference reference) -> priority(reference.evidenceCode()))
                        .thenComparing(reference -> reference.artifactId().map(UUID::toString).orElse("")))
                .map(reference -> new RecommendationExplanationMessage(
                        reference.evidenceCode(),
                        reference.problemSessionId(),
                        reference.artifactId(),
                        messageFor(reference.evidenceCode())))
                .distinct()
                .limit(MAX_MESSAGES)
                .toList();

        return new RecommendationExplanation(recommendedProblemSessionId, algorithmVersion, messages);
    }

    private static int priority(SimilarityEvidenceCode evidenceCode) {
        return switch (evidenceCode) {
            case ERROR_PHRASE -> 0;
            case LEXICAL_TEXT -> 1;
            case TECHNOLOGY -> 2;
            case VECTOR_DISTANCE -> 3;
        };
    }

    private static String messageFor(SimilarityEvidenceCode evidenceCode) {
        return switch (evidenceCode) {
            case ERROR_PHRASE -> "The same error phrase appeared in the prior solved problem.";
            case LEXICAL_TEXT -> "The prior problem and solution matched the current problem text.";
            case TECHNOLOGY -> "The prior solved problem used overlapping technologies.";
            case VECTOR_DISTANCE -> "The prior solved problem passed Trail's semantic similarity threshold.";
        };
    }
}
