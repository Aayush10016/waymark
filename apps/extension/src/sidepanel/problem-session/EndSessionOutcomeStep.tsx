import type { CSSProperties, ReactElement } from "react";
import { useReducer, useState } from "react";
import type { ActiveProblemSessionPayload, ProblemSessionResponsePayload } from "@trail/extension-messages";
import type {
  EndSessionProblemSessionClient,
  EndSessionProblemSessionClientResult
} from "./end-session-client";
import {
  endSessionPanelTransitionMilliseconds,
  initialEndSessionState,
  reduceEndSessionState
} from "./end-session-state";
import {
  AuthenticationRequiredError,
  FinalSolutionEntryStep,
  type ReviewableSessionArtifact
} from "./FinalSolutionEntryStep";
import type { ProblemSummarySaveStatus } from "./ProblemSummary";

export interface EndSessionOutcomeStepProps {
  readonly artifacts?: readonly ReviewableSessionArtifact[];
  readonly client: EndSessionProblemSessionClient;
  readonly initialProblemSessionState: ProblemSessionResponsePayload;
  readonly prefersReducedMotion?: boolean;
}

export function EndSessionOutcomeStep(props: EndSessionOutcomeStepProps): ReactElement {
  const [problemSessionState, setProblemSessionState] = useState(props.initialProblemSessionState);
  const [state, dispatch] = useReducer(reduceEndSessionState, undefined, initialEndSessionState);
  const [selectedSolutionSession, setSelectedSolutionSession] = useState<ActiveProblemSessionPayload | null>(
    props.initialProblemSessionState.state.activeSession
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const activeSession = problemSessionState.state.activeSession;
  const solutionSession = state.step === "solution_ready" ? selectedSolutionSession : activeSession;
  const transitionMilliseconds = endSessionPanelTransitionMilliseconds(props.prefersReducedMotion === true);

  async function dismissNotProblem(): Promise<void> {
    setPending(true);
    const result = await props.client.dismiss();
    applyClientResult(result, setProblemSessionState, setError);
    if (result.status === "received") {
      dispatch({ type: "NOT_PROBLEM_DISMISSED" });
    }
    setPending(false);
  }

  async function saveSolution(finalSolution: string): Promise<ProblemSummarySaveStatus> {
    const result = await props.client.saveSolution(finalSolution);
    if (result.status !== "received") {
      throw new Error("Problem Session solution response unavailable.");
    }
    applyClientResult(result, setProblemSessionState, setError);
    switch (result.payload.commandStatus) {
      case "received":
        return "saved";
      case "network_error":
        return "offline";
      case "authentication_required":
        throw new AuthenticationRequiredError();
      default:
        throw new Error("Problem Session solution save failed.");
    }
  }

  if (activeSession === null && state.step === "outcome") {
    return (
      <main aria-label="Trail end session" style={styles.shell}>
        <section style={styles.card}>
          <p style={styles.eyebrow}>End session</p>
          <h1 style={styles.title}>No active problem</h1>
          <p style={styles.body}>There is no active Problem Session to finish.</p>
        </section>
      </main>
    );
  }

  return (
    <main aria-label="Trail end session" style={styles.shell}>
      <section
        aria-labelledby="trail-end-session-title"
        style={{ ...styles.card, transitionDuration: `${transitionMilliseconds}ms` }}
      >
        {state.step === "outcome" && activeSession !== null ? (
          <OutcomeQuestion
            disabled={pending}
            error={error}
            onNotProblem={() => void dismissNotProblem()}
            onNotYet={() => dispatch({ type: "SELECT_NOT_YET" })}
            onRemind={() => dispatch({ type: "SELECT_REMIND" })}
            onSolved={() => {
              setSelectedSolutionSession(activeSession);
              dispatch({ type: "SELECT_SOLVED" });
            }}
            session={activeSession}
          />
        ) : null}
        {state.step === "solution_ready" && solutionSession !== null ? (
          <FinalSolutionEntryStep
            {...(props.artifacts === undefined ? {} : { artifacts: props.artifacts })}
            onBack={() => dispatch({ type: "BACK_TO_OUTCOME" })}
            onSaveSolution={saveSolution}
            session={solutionSession}
          />
        ) : null}
        {state.step === "keep_for_later" ? (
          <OutcomeResult label="Not yet" onBack={() => dispatch({ type: "BACK_TO_OUTCOME" })} title="Kept for later">
            Trail will keep this unresolved problem available locally. Final-solution entry is skipped.
          </OutcomeResult>
        ) : null}
        {state.step === "reminded" ? (
          <OutcomeResult label="Ask me later" onBack={() => dispatch({ type: "BACK_TO_OUTCOME" })} title="Reminder deferred">
            Trail will stay quiet for now. Only one reminder is allowed for this session.
          </OutcomeResult>
        ) : null}
        {state.step === "dismissed" ? (
          <OutcomeResult label="Not a problem" onBack={() => dispatch({ type: "BACK_TO_OUTCOME" })} title="Dismissed">
            This session was dismissed as not a problem. You can undo from the popup while the undo window is active.
          </OutcomeResult>
        ) : null}
      </section>
    </main>
  );
}

function OutcomeQuestion(props: {
  readonly disabled: boolean;
  readonly error: string | null;
  readonly onNotProblem: () => void;
  readonly onNotYet: () => void;
  readonly onRemind: () => void;
  readonly onSolved: () => void;
  readonly session: ActiveProblemSessionPayload;
}): ReactElement {
  return (
    <>
      <p style={styles.eyebrow}>End session</p>
      <h1 id="trail-end-session-title" style={styles.title}>Did you solve it?</h1>
      <p style={styles.body}>{props.session.title}</p>
      <p style={styles.meta}>{sessionMeta(props.session)}</p>
      <div aria-label="Choose outcome" style={styles.actions}>
        <button autoFocus disabled={props.disabled} onClick={props.onSolved} style={buttonStyle("primary")} type="button">
          Solved
        </button>
        <button disabled={props.disabled} onClick={props.onNotYet} style={buttonStyle("secondary")} type="button">
          Not yet
        </button>
        <button disabled={props.disabled} onClick={props.onNotProblem} style={buttonStyle("secondary")} type="button">
          Not a problem
        </button>
        <button disabled={props.disabled} onClick={props.onRemind} style={buttonStyle("ghost")} type="button">
          Ask me later
        </button>
      </div>
      {props.error === null ? null : <p style={styles.error}>{props.error}</p>}
    </>
  );
}

function OutcomeResult(props: {
  readonly children: string;
  readonly label: string;
  readonly onBack: () => void;
  readonly title: string;
}): ReactElement {
  return (
    <>
      <p style={styles.eyebrow}>{props.label}</p>
      <h1 id="trail-end-session-title" style={styles.title}>{props.title}</h1>
      <p style={styles.body}>{props.children}</p>
      <button autoFocus onClick={props.onBack} style={buttonStyle("secondary")} type="button">
        Back
      </button>
    </>
  );
}

function applyClientResult(
  result: EndSessionProblemSessionClientResult,
  setState: (state: ProblemSessionResponsePayload) => void,
  setError: (error: string | null) => void
): void {
  if (result.status !== "received") {
    setError("Problem Session state could not update. Try reopening Trail.");
    return;
  }

  setState(result.payload);
  setError(null);
}

function sessionMeta(session: ActiveProblemSessionPayload): string {
  if (session.endedAt !== null) {
    return "Capture boundary closed. Choose what happened.";
  }

  return session.source === "MANUAL" ? "Started manually. Choose what happened." : "Detected by Trail. Choose what happened.";
}

function buttonStyle(variant: "ghost" | "primary" | "secondary"): CSSProperties {
  const base: CSSProperties = {
    borderRadius: "10px",
    cursor: "pointer",
    fontSize: "13px",
    fontWeight: 650,
    padding: "10px 12px"
  };

  if (variant === "primary") {
    return { ...base, background: "#8B7CFF", border: "1px solid #A79DFF", color: "#07080A" };
  }

  if (variant === "secondary") {
    return { ...base, background: "#181C22", border: "1px solid #303744", color: "#F4F7FB" };
  }

  return { ...base, background: "transparent", border: "1px solid #303744", color: "#AEB8C5" };
}

const styles = {
  actions: {
    display: "grid",
    gap: "8px",
    marginTop: "16px"
  },
  body: {
    color: "#AEB8C5",
    fontSize: "14px",
    lineHeight: 1.5,
    margin: "8px 0 0"
  },
  card: {
    background: "#11141A",
    border: "1px solid #303744",
    borderRadius: "16px",
    padding: "16px",
    transitionProperty: "opacity"
  },
  error: {
    color: "#F0A1A8",
    fontSize: "12px",
    margin: "10px 0 0"
  },
  eyebrow: {
    color: "#8B7CFF",
    fontSize: "11px",
    fontWeight: 700,
    letterSpacing: "0.02em",
    margin: "0 0 6px",
    textTransform: "uppercase"
  },
  meta: {
    color: "#707987",
    fontSize: "12px",
    margin: "8px 0 0"
  },
  shell: {
    background: "#07080A",
    color: "#F4F7FB",
    display: "grid",
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif",
    gap: "12px",
    minHeight: "100vh",
    padding: "14px"
  },
  title: {
    color: "#F4F7FB",
    fontSize: "20px",
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
