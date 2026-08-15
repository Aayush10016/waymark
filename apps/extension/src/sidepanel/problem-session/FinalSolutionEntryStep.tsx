import type { CSSProperties, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import type { ActiveProblemSessionPayload } from "@trail/extension-messages";
import { IndexedDbFinalSolutionDraftRepository } from "../../storage/drafts";
import { LocalFinalSolutionDraftService, type FinalSolutionDraftService } from "../../services/drafts";
import {
  createContextReviewState,
  removeContextArtifact,
  undoRemoveContextArtifact
} from "./context-review-state";
import { ProblemSummary, type ProblemSummarySaveStatus } from "./ProblemSummary";

export interface ReviewableSessionArtifact {
  readonly artifactType: string;
  readonly hostname: string;
  readonly id: string;
  readonly occurredAt: string;
  readonly title: string | null;
}

export interface FinalSolutionEntryStepProps {
  readonly artifacts?: readonly ReviewableSessionArtifact[];
  readonly draftService?: FinalSolutionDraftService;
  readonly onBack: () => void;
  readonly onSaveSolution?: (finalSolution: string) => Promise<ProblemSummarySaveStatus>;
  readonly session: ActiveProblemSessionPayload;
}

export function FinalSolutionEntryStep(props: FinalSolutionEntryStepProps): ReactElement {
  const draftService = useMemo(
    () => props.draftService ?? new LocalFinalSolutionDraftService(new IndexedDbFinalSolutionDraftRepository()),
    [props.draftService]
  );
  const [text, setText] = useState("");
  const [draftStatus, setDraftStatus] = useState<"idle" | "loading" | "saved" | "unavailable">("loading");
  const [saveStatus, setSaveStatus] = useState<"auth" | "error" | "idle" | "saving">("idle");
  const [summary, setSummary] = useState<{ readonly status: ProblemSummarySaveStatus; readonly solution: string } | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [contextReviewState, setContextReviewState] = useState(() => createContextReviewState(props.artifacts ?? []));

  useEffect(() => {
    let active = true;
    void draftService
      .read(props.session.id)
      .then((draft) => {
        if (!active) {
          return;
        }
        setText(draft?.text ?? "");
        setDraftStatus("idle");
      })
      .catch(() => {
        if (active) {
          setDraftStatus("unavailable");
        }
      });

    return () => {
      active = false;
    };
  }, [draftService, props.session.id]);

  async function autosave(value: string): Promise<void> {
    setText(value);
    try {
      await draftService.save(props.session.id, value);
      setDraftStatus("saved");
    } catch {
      setDraftStatus("unavailable");
    }
  }

  function removeArtifact(artifactId: string): void {
    setContextReviewState((current) => removeContextArtifact(current, artifactId));
  }

  function undoRemoveArtifact(artifactId: string): void {
    setContextReviewState((current) => undoRemoveContextArtifact(current, artifactId));
  }

  async function saveSolution(): Promise<void> {
    if (!meaningfulSolution) {
      return;
    }
    setSaveStatus("saving");
    try {
      const result = props.onSaveSolution === undefined ? "saved" : await props.onSaveSolution(text);
      await draftService.acknowledgeDeleted(props.session.id);
      setSummary({ solution: text, status: result });
      setSaveStatus("idle");
    } catch (error) {
      setSaveStatus(error instanceof AuthenticationRequiredError ? "auth" : "error");
    }
  }

  async function saveSummaryEdit(finalSolution: string): Promise<ProblemSummarySaveStatus> {
    const result = props.onSaveSolution === undefined ? "saved" : await props.onSaveSolution(finalSolution);
    await draftService.save(props.session.id, finalSolution);
    if (result === "saved") {
      await draftService.acknowledgeDeleted(props.session.id);
    }
    return result;
  }

  const meaningfulSolution = text.trim().length > 0;

  if (summary !== null) {
    return (
      <ProblemSummary
        artifacts={contextReviewState.visibleArtifacts}
        initialStatus={summary.status}
        onSaveEdit={saveSummaryEdit}
        session={{ ...props.session, status: "SOLVED" }}
        solution={summary.solution}
      />
    );
  }

  return (
    <>
      <p style={styles.eyebrow}>Solved</p>
      <h1 id="trail-end-session-title" style={styles.title}>What finally solved it?</h1>
      <p style={styles.body}>One or two sentences is enough. Trail will not generate this for you.</p>
      <textarea
        aria-label="Final solution"
        autoFocus
        onChange={(event) => void autosave(event.currentTarget.value)}
        placeholder="Allowed localhost:3000 in CorsConfiguration and placed the JWT filter before UsernamePasswordAuthenticationFilter."
        style={styles.textarea}
        value={text}
      />
      <p aria-live="polite" style={draftStatus === "unavailable" ? styles.error : styles.meta}>
        {draftStatusCopy(draftStatus)}
      </p>
      <div style={styles.actions}>
        <button disabled={!meaningfulSolution || saveStatus === "saving"} onClick={() => void saveSolution()} style={buttonStyle("primary")} type="button">
          {saveStatus === "saving" ? "Saving..." : "Save solution"}
        </button>
        <button aria-expanded={reviewOpen} onClick={() => setReviewOpen((current) => !current)} style={buttonStyle("secondary")} type="button">
          Review context
        </button>
        <button onClick={props.onBack} style={buttonStyle("ghost")} type="button">
          Back
        </button>
      </div>
      {saveStatus === "auth" ? (
        <p style={styles.error}>Sign in again to save this solution. Your draft stays on this device.</p>
      ) : null}
      {saveStatus === "error" ? (
        <p style={styles.error}>
          Solution could not be saved. Your draft is preserved.{" "}
          <button onClick={() => void saveSolution()} style={inlineButtonStyle} type="button">
            Retry
          </button>
        </p>
      ) : null}
      {reviewOpen ? (
        <ContextReview
          artifacts={contextReviewState.visibleArtifacts}
          onRemove={removeArtifact}
          onUndo={undoRemoveArtifact}
          removedArtifacts={contextReviewState.removedArtifacts}
        />
      ) : null}
    </>
  );
}

export class AuthenticationRequiredError extends Error {
  constructor() {
    super("authentication required");
  }
}

function ContextReview(props: {
  readonly artifacts: readonly ReviewableSessionArtifact[];
  readonly onRemove: (artifactId: string) => void;
  readonly onUndo: (artifactId: string) => void;
  readonly removedArtifacts: readonly ReviewableSessionArtifact[];
}): ReactElement {
  return (
    <section aria-label="Captured context review" style={styles.contextPanel}>
      <h2 style={styles.contextTitle}>Context Trail will remember</h2>
      {props.artifacts.length === 0 ? (
        <p style={styles.body}>There isn’t much captured context, but your solution can still be remembered.</p>
      ) : (
        <ol style={styles.artifactList}>
          {props.artifacts.map((artifact) => (
            <li key={artifact.id} style={styles.artifactItem}>
              <span style={styles.artifactTitle}>{artifact.title ?? artifact.hostname}</span>
              <span style={styles.meta}>{artifact.hostname}</span>
              <button onClick={() => props.onRemove(artifact.id)} style={buttonStyle("ghost")} type="button">
                Remove
              </button>
            </li>
          ))}
        </ol>
      )}
      {props.removedArtifacts.length === 0 ? null : (
        <ul aria-label="Removed context" style={styles.removedList}>
          {props.removedArtifacts.map((artifact) => (
            <li key={artifact.id} style={styles.notice}>
              Removed {artifact.title ?? artifact.hostname}.{" "}
              <button onClick={() => props.onUndo(artifact.id)} style={inlineButtonStyle} type="button">
                Undo
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function draftStatusCopy(status: "idle" | "loading" | "saved" | "unavailable"): string {
  switch (status) {
    case "idle":
      return "Draft ready on this device.";
    case "loading":
      return "Loading local draft…";
    case "saved":
      return "Draft saved on this device.";
    case "unavailable":
      return "Local draft could not be saved. Keep this panel open and try again.";
  }
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

const inlineButtonStyle: CSSProperties = {
  background: "transparent",
  border: 0,
  color: "#A79DFF",
  cursor: "pointer",
  font: "inherit",
  fontWeight: 700,
  padding: 0
};

const styles = {
  actions: {
    display: "grid",
    gap: "8px",
    marginTop: "12px"
  },
  artifactItem: {
    borderTop: "1px solid #262B33",
    display: "grid",
    gap: "4px",
    padding: "10px 0"
  },
  artifactList: {
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  artifactTitle: {
    color: "#F4F7FB",
    fontSize: "13px",
    fontWeight: 650
  },
  body: {
    color: "#AEB8C5",
    fontSize: "14px",
    lineHeight: 1.5,
    margin: "8px 0 0"
  },
  contextPanel: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "12px",
    marginTop: "14px",
    padding: "12px"
  },
  contextTitle: {
    color: "#F4F7FB",
    fontSize: "14px",
    margin: "0 0 8px"
  },
  error: {
    color: "#F0A1A8",
    fontSize: "12px",
    margin: "8px 0 0"
  },
  eyebrow: {
    color: "#62D49C",
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
  notice: {
    color: "#D7DEE8",
    fontSize: "12px"
  },
  removedList: {
    display: "grid",
    gap: "6px",
    listStyle: "none",
    margin: "10px 0 0",
    padding: 0
  },
  textarea: {
    background: "#0B0D10",
    border: "1px solid #303744",
    borderRadius: "12px",
    color: "#F4F7FB",
    font: "inherit",
    lineHeight: 1.5,
    marginTop: "12px",
    minHeight: "132px",
    padding: "12px",
    resize: "vertical"
  },
  title: {
    color: "#F4F7FB",
    fontSize: "20px",
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
