import { describe, expect, it } from "vitest";
import {
  endSessionPanelTransitionMilliseconds,
  initialEndSessionState,
  reduceEndSessionState
} from "../src/sidepanel/problem-session";

describe("End Session outcome state", () => {
  it("starts with no preselected outcome", () => {
    expect(initialEndSessionState()).toEqual({
      selectedOutcome: null,
      step: "outcome"
    });
  });

  it("transitions solved outcome to the deferred final-solution step", () => {
    expect(reduceEndSessionState(initialEndSessionState(), { type: "SELECT_SOLVED" })).toEqual({
      selectedOutcome: "solved",
      step: "solution_ready"
    });
  });

  it("transitions not-yet outcome without saving a solution", () => {
    expect(reduceEndSessionState(initialEndSessionState(), { type: "SELECT_NOT_YET" })).toEqual({
      selectedOutcome: "not_yet",
      step: "keep_for_later"
    });
  });

  it("transitions remind outcome without scheduling future milestone work", () => {
    expect(reduceEndSessionState(initialEndSessionState(), { type: "SELECT_REMIND" })).toEqual({
      selectedOutcome: "remind",
      step: "reminded"
    });
  });

  it("transitions not-problem only after dismissal acknowledgement", () => {
    expect(reduceEndSessionState(initialEndSessionState(), { type: "NOT_PROBLEM_DISMISSED" })).toEqual({
      selectedOutcome: "not_problem",
      step: "dismissed"
    });
  });

  it("returns to the outcome step without preserving a preselected outcome", () => {
    const solved = reduceEndSessionState(initialEndSessionState(), { type: "SELECT_SOLVED" });

    expect(reduceEndSessionState(solved, { type: "BACK_TO_OUTCOME" })).toEqual(initialEndSessionState());
  });

  it("uses no motion duration when reduced motion is requested", () => {
    expect(endSessionPanelTransitionMilliseconds(true)).toBe(0);
    expect(endSessionPanelTransitionMilliseconds(false)).toBe(120);
  });
});
