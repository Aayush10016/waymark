package io.github.aayush10016.trail.retrieval.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolvedProblemSearchQueryTest {

    @Test
    void normalizesAndBoundsSearchSignals() {
        var query = new SolvedProblemSearchQuery(
                UUID.randomUUID(),
                "  Spring   CORS  ",
                java.util.Arrays.asList(" Spring Security ", "spring security", null),
                List.of(" CORS "),
                10);

        assertThat(query.queryText()).isEqualTo("spring cors");
        assertThat(query.technologies()).containsExactly("spring security");
        assertThat(query.errorPhrases()).containsExactly("cors");
    }

    @Test
    void allowsRecentOnlySearchAndRejectsInvalidLimit() {
        var userId = UUID.randomUUID();
        var query = new SolvedProblemSearchQuery(userId, " ", List.of(), List.of(), 10);

        assertThat(query.isRecentOnly()).isTrue();
        assertThatThrownBy(() -> new SolvedProblemSearchQuery(userId, "cors", List.of(), List.of(), 52))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsProblemTypeOnlySearchAsDeterministicFilter() {
        var query = new SolvedProblemSearchQuery(
                UUID.randomUUID(),
                null,
                List.of(),
                List.of(),
                List.of(" configuration "),
                10,
                0);

        assertThat(query.queryText()).isNull();
        assertThat(query.problemTypes()).containsExactly("configuration");
        assertThat(query.isRecentOnly()).isFalse();
    }
}
