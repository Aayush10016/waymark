package io.github.aayush10016.trail.retrieval.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhyThisExplanationBuilderTest {

    private static final UUID RECOMMENDED_SESSION_ID = UUID.fromString("70000000-0000-4000-8000-000000000071");
    private static final UUID OTHER_SESSION_ID = UUID.fromString("70000000-0000-4000-8000-000000000072");
    private static final UUID ARTIFACT_ID = UUID.fromString("71000000-0000-4000-8000-000000000071");

    private final WhyThisExplanationBuilder builder = new WhyThisExplanationBuilder();

    @Test
    void mapsEveryMessageToEvidenceCodeAndSourceIds() {
        var explanation = builder.build(
                RECOMMENDED_SESSION_ID,
                "hybrid-similarity-v1",
                List.of(
                        RecommendationEvidenceReference.session(RECOMMENDED_SESSION_ID, SimilarityEvidenceCode.TECHNOLOGY),
                        RecommendationEvidenceReference.artifact(
                                RECOMMENDED_SESSION_ID, ARTIFACT_ID, SimilarityEvidenceCode.ERROR_PHRASE),
                        RecommendationEvidenceReference.session(RECOMMENDED_SESSION_ID, SimilarityEvidenceCode.LEXICAL_TEXT)));

        assertThat(explanation.recommendedProblemSessionId()).isEqualTo(RECOMMENDED_SESSION_ID);
        assertThat(explanation.messages()).hasSize(3);
        assertThat(explanation.messages())
                .allSatisfy(message -> {
                    assertThat(message.problemSessionId()).isEqualTo(RECOMMENDED_SESSION_ID);
                    assertThat(message.evidenceCode()).isNotNull();
                    assertThat(message.message()).isNotBlank();
                });
        assertThat(explanation.messages().get(0).evidenceCode()).isEqualTo(SimilarityEvidenceCode.ERROR_PHRASE);
        assertThat(explanation.messages().get(0).artifactId()).contains(ARTIFACT_ID);
    }

    @Test
    void ignoresEvidenceForOtherSessionsAndDoesNotExposeGeneratedRationaleOrRawScores() {
        var explanation = builder.build(
                RECOMMENDED_SESSION_ID,
                "hybrid-similarity-v1",
                List.of(
                        RecommendationEvidenceReference.session(OTHER_SESSION_ID, SimilarityEvidenceCode.ERROR_PHRASE),
                        RecommendationEvidenceReference.session(RECOMMENDED_SESSION_ID, SimilarityEvidenceCode.VECTOR_DISTANCE)));

        assertThat(explanation.messages()).hasSize(1);
        assertThat(explanation.messages().get(0).message())
                .doesNotContain("score")
                .doesNotContain("0.")
                .doesNotContain("91%")
                .doesNotContain("model")
                .doesNotContain("generated");
    }

    @Test
    void failsPredictablyWhenNoEvidenceMapsToTheRecommendedSession() {
        assertThatThrownBy(() -> builder.build(
                        RECOMMENDED_SESSION_ID,
                        "hybrid-similarity-v1",
                        List.of(RecommendationEvidenceReference.session(OTHER_SESSION_ID, SimilarityEvidenceCode.ERROR_PHRASE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explanation requires");
    }
}
