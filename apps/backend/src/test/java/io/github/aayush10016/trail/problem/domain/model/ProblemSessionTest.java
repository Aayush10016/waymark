package io.github.aayush10016.trail.problem.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProblemSessionTest {

    private static final Instant SOLUTION_AUTHORED_AT = Instant.parse("2026-07-15T08:00:00Z");

    @Test
    void startsAsCandidateWithoutFinalSolution() {
        var session = ProblemSession.candidate();

        assertThat(session.status()).isEqualTo(ProblemSessionStatus.CANDIDATE);
        assertThat(session.finalSolution()).isEmpty();
        assertThat(session.isTerminal()).isFalse();
    }

    @Test
    void supportsValidCandidateTransitions() {
        assertThat(ProblemSession.candidate().markReviewable().status()).isEqualTo(ProblemSessionStatus.REVIEWABLE);
        assertThat(ProblemSession.candidate().dismiss().status()).isEqualTo(ProblemSessionStatus.DISMISSED);
        assertThat(ProblemSession.candidate().markUnsolved().status()).isEqualTo(ProblemSessionStatus.UNSOLVED);
    }

    @Test
    void supportsValidReviewableTransitions() {
        var solution = FinalSolution.userAuthored("Allowed localhost:3000 in CorsConfiguration.", SOLUTION_AUTHORED_AT);

        assertThat(ProblemSession.candidate().markReviewable().solve(solution).status())
                .isEqualTo(ProblemSessionStatus.SOLVED);
        assertThat(ProblemSession.candidate().markReviewable().dismiss().status())
                .isEqualTo(ProblemSessionStatus.DISMISSED);
        assertThat(ProblemSession.candidate().markReviewable().markUnsolved().status())
                .isEqualTo(ProblemSessionStatus.UNSOLVED);
    }

    @Test
    void solvedSessionRequiresAndPreservesExactUserAuthoredSolution() {
        var exactText = "  Added the JWT filter before UsernamePasswordAuthenticationFilter.  ";
        var solution = FinalSolution.userAuthored(exactText, SOLUTION_AUTHORED_AT);

        var solved = ProblemSession.candidate().markReviewable().solve(solution);

        assertThat(solved.status()).isEqualTo(ProblemSessionStatus.SOLVED);
        assertThat(solved.finalSolution()).contains(solution);
        assertThat(solved.finalSolution().orElseThrow().text()).isEqualTo(exactText);
        assertThat(solved.finalSolution().orElseThrow().author()).isEqualTo(FinalSolutionAuthor.USER);
        assertThat(solved.finalSolution().orElseThrow().authoredAt()).isEqualTo(SOLUTION_AUTHORED_AT);
        assertThat(solved.isTerminal()).isFalse();
    }

    @Test
    void userCanReplaceExactSolutionWhileSessionRemainsSolved() {
        var revised = solvedSession()
                .updateSolution(FinalSolution.userAuthored("Revised exact solution.", SOLUTION_AUTHORED_AT.plusSeconds(60)));

        assertThat(revised.status()).isEqualTo(ProblemSessionStatus.SOLVED);
        assertThat(revised.finalSolution().orElseThrow().text()).isEqualTo("Revised exact solution.");
        assertThat(revised.finalSolution().orElseThrow().author()).isEqualTo(FinalSolutionAuthor.USER);
    }

    @Test
    void rejectsBlankFinalSolutionText() {
        assertThatThrownBy(() -> FinalSolution.userAuthored("   ", SOLUTION_AUTHORED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("final solution text must not be blank");
    }

    @Test
    void rejectsNonUserAuthoredSolution() {
        var aiSolution = new FinalSolution(
                "Generated suggestion that must not solve the session.",
                FinalSolutionAuthor.AI,
                SOLUTION_AUTHORED_AT);

        assertThatThrownBy(() -> ProblemSession.candidate().markReviewable().solve(aiSolution))
                .isInstanceOfSatisfying(ProblemSessionTransitionException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("ILLEGAL_PROBLEM_SESSION_TRANSITION");
                    assertThat(exception.currentStatus()).isEqualTo(ProblemSessionStatus.REVIEWABLE);
                    assertThat(exception.attemptedStatus()).isEqualTo(ProblemSessionStatus.SOLVED);
                });
    }

    @Test
    void supportsSolvedToDeletedTransitionAndClearsFinalSolution() {
        var deleted = solvedSession().delete();

        assertThat(deleted.status()).isEqualTo(ProblemSessionStatus.DELETED);
        assertThat(deleted.finalSolution()).isEmpty();
        assertThat(deleted.isTerminal()).isTrue();
    }

    @Test
    void rejectsIllegalTransitionsPredictably() {
        assertIllegalTransition(ProblemSession.candidate(), ProblemSessionStatus.SOLVED, () -> ProblemSession.candidate()
                .solve(FinalSolution.userAuthored("Solved", SOLUTION_AUTHORED_AT)));
        assertIllegalTransition(ProblemSession.candidate(), ProblemSessionStatus.DELETED, () -> ProblemSession.candidate()
                .delete());
        assertIllegalTransition(ProblemSession.candidate().markReviewable(), ProblemSessionStatus.REVIEWABLE, () ->
                ProblemSession.candidate().markReviewable().markReviewable());
        assertIllegalTransition(solvedSession(), ProblemSessionStatus.DISMISSED, () -> solvedSession().dismiss());
        assertIllegalTransition(solvedSession(), ProblemSessionStatus.UNSOLVED, () -> solvedSession().markUnsolved());
        assertIllegalTransition(solvedSession(), ProblemSessionStatus.SOLVED, () -> solvedSession()
                .solve(FinalSolution.userAuthored("Solved again", SOLUTION_AUTHORED_AT)));
    }

    @Test
    void terminalStatesRejectEveryTransition() {
        assertTerminalStateRejectsTransitions(ProblemSession.candidate().dismiss());
        assertTerminalStateRejectsTransitions(ProblemSession.candidate().markUnsolved());
        assertTerminalStateRejectsTransitions(solvedSession().delete());
    }

    private static ProblemSession solvedSession() {
        return ProblemSession.candidate()
                .markReviewable()
                .solve(FinalSolution.userAuthored("Fixed CORS configuration.", SOLUTION_AUTHORED_AT));
    }

    private static void assertTerminalStateRejectsTransitions(ProblemSession session) {
        assertThat(session.isTerminal()).isTrue();
        assertIllegalTransition(session, ProblemSessionStatus.REVIEWABLE, session::markReviewable);
        assertIllegalTransition(
                session,
                ProblemSessionStatus.SOLVED,
                () -> session.solve(FinalSolution.userAuthored("Solved", SOLUTION_AUTHORED_AT)));
        assertIllegalTransition(session, ProblemSessionStatus.DISMISSED, session::dismiss);
        assertIllegalTransition(session, ProblemSessionStatus.UNSOLVED, session::markUnsolved);
        assertIllegalTransition(session, ProblemSessionStatus.DELETED, session::delete);
    }

    private static void assertIllegalTransition(
            ProblemSession session, ProblemSessionStatus attemptedStatus, Runnable transition) {
        assertThatThrownBy(transition::run)
                .isInstanceOfSatisfying(ProblemSessionTransitionException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("ILLEGAL_PROBLEM_SESSION_TRANSITION");
                    assertThat(exception.currentStatus()).isEqualTo(session.status());
                    assertThat(exception.attemptedStatus()).isEqualTo(attemptedStatus);
                    assertThat(exception.getMessage()).contains(session.status().name(), attemptedStatus.name());
                });
    }
}
