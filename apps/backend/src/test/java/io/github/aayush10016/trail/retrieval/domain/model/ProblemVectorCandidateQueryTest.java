package io.github.aayush10016.trail.retrieval.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemVectorCandidateQueryTest {

    @Test
    void createsImmutableValidatedQuery() {
        var vector = new java.util.ArrayList<>(List.of(0.1, 0.2, 0.3));
        var query = new ProblemVectorCandidateQuery(
                UUID.randomUUID(), " solved-session ", vector, 10, Duration.ofSeconds(2));

        vector.add(0.4);

        assertThat(query.generationKey()).isEqualTo("solved-session");
        assertThat(query.vector()).containsExactly(0.1, 0.2, 0.3);
        assertThatThrownBy(() -> query.vector().add(0.4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidVectorCandidateQueries() {
        var userId = UUID.randomUUID();

        assertThatThrownBy(() -> new ProblemVectorCandidateQuery(
                        userId, " ", List.of(0.1, 0.2, 0.3), 10, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProblemVectorCandidateQuery(
                        userId, "solved-session", List.of(), 10, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProblemVectorCandidateQuery(
                        userId, "solved-session", List.of(Double.NaN), 10, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProblemVectorCandidateQuery(
                        userId, "solved-session", List.of(0.1), 51, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProblemVectorCandidateQuery(
                        userId, "solved-session", List.of(0.1), 10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
