export type EndSessionOutcome = "not_problem" | "not_yet" | "remind" | "solved";
export type EndSessionStep =
  | "outcome"
  | "dismissed"
  | "keep_for_later"
  | "reminded"
  | "solution_ready";

export interface EndSessionState {
  readonly selectedOutcome: EndSessionOutcome | null;
  readonly step: EndSessionStep;
}

export type EndSessionAction =
  | { readonly type: "BACK_TO_OUTCOME" }
  | { readonly type: "NOT_PROBLEM_DISMISSED" }
  | { readonly type: "SELECT_NOT_YET" }
  | { readonly type: "SELECT_REMIND" }
  | { readonly type: "SELECT_SOLVED" };

export function initialEndSessionState(): EndSessionState {
  return Object.freeze({
    selectedOutcome: null,
    step: "outcome"
  });
}

export function reduceEndSessionState(_state: EndSessionState, action: EndSessionAction): EndSessionState {
  switch (action.type) {
    case "BACK_TO_OUTCOME":
      return initialEndSessionState();
    case "NOT_PROBLEM_DISMISSED":
      return Object.freeze({ selectedOutcome: "not_problem", step: "dismissed" });
    case "SELECT_NOT_YET":
      return Object.freeze({ selectedOutcome: "not_yet", step: "keep_for_later" });
    case "SELECT_REMIND":
      return Object.freeze({ selectedOutcome: "remind", step: "reminded" });
    case "SELECT_SOLVED":
      return Object.freeze({ selectedOutcome: "solved", step: "solution_ready" });
  }
}

export function endSessionPanelTransitionMilliseconds(reducedMotion: boolean): number {
  return reducedMotion ? 0 : 120;
}
