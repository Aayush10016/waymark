import type { CSSProperties, ReactElement } from "react";
import { useState } from "react";
import type { ActiveProblemSessionPayload } from "@trail/extension-messages";
import type { ReviewableSessionArtifact } from "./FinalSolutionEntryStep";

export type ProblemSummarySaveStatus = "offline" | "saved";

export interface ProblemSummaryProps {
  readonly artifacts?: readonly ReviewableSessionArtifact[];
  readonly initialStatus?: ProblemSummarySaveStatus;
  readonly onSaveEdit?: (finalSolution: string) => Promise<ProblemSummarySaveStatus>;
  readonly session: ActiveProblemSessionPayload;
  readonly solution: string;
}

/**
 * Solution-first summary for a solved Problem Session.
 */
export function ProblemSummary(props: ProblemSummaryProps): ReactElement {
  const [solution, setSolution] = useState(props.solution);
  const [draft, setDraft] = useState(props.solution);
  const [editing, setEditing] = useState(false);
  const [status, setStatus] = useState<"editing" | "error" | "loading" | ProblemSummarySaveStatus>(
    props.initialStatus ?? "saved"
  );
  const decisiveArtifacts = props.artifacts ?? [];

  async function saveEdit(): Promise<void> {
    if (draft.trim().length === 0 || props.onSaveEdit === undefined) {
      return;
    }
    setStatus("loading");
    try {
      const result = await props.onSaveEdit(draft);
      setSolution(draft);
      setEditing(false);
      setStatus(result);
    } catch {
      setStatus("error");
    }
  }

  return (
    <article aria-label="Problem Summary" style={styles.shell}>
      <header style={styles.header}>
        <p style={styles.eyebrow}>Saved to your engineering memory</p>
        <h1 style={styles.title}>{props.session.title}</h1>
        <p style={styles.meta}>{summaryMeta(props.session)}</p>
      </header>

      <section aria-label="Your solution" style={styles.solutionCard}>
        <div style={styles.solutionHeader}>
          <span style={styles.solutionLabel}>Your solution</span>
          <span style={styles.authorLabel}>User-authored</span>
        </div>
        {editing ? (
          <>
            <textarea
              aria-label="Edit solution"
              autoFocus
              onChange={(event) => {
                setDraft(event.currentTarget.value);
                setStatus("editing");
              }}
              style={styles.textarea}
              value={draft}
            />
            <div style={styles.actions}>
              <button disabled={draft.trim().length === 0 || status === "loading"} onClick={() => void saveEdit()} style={buttonStyle("primary")} type="button">
                {status === "loading" ? "Saving..." : "Save"}
              </button>
              <button
                onClick={() => {
                  setDraft(solution);
                  setEditing(false);
                  setStatus(props.initialStatus ?? "saved");
                }}
                style={buttonStyle("secondary")}
                type="button"
              >
                Cancel
              </button>
            </div>
          </>
        ) : (
          <>
            <p style={styles.solutionText}>{solution}</p>
            <button onClick={() => setEditing(true)} style={buttonStyle("ghost")} type="button">
              Edit solution
            </button>
          </>
        )}
        <p aria-live="polite" style={status === "error" ? styles.error : styles.meta}>
          {statusCopy(status)}
        </p>
      </section>

      <section aria-label="Matching details" style={styles.section}>
        <h2 style={styles.sectionTitle}>Matching details</h2>
        <p role="status" style={styles.meta}>
          Semantic matching is not required for this saved problem. Your solution is saved, and persisted Trail resources
          are available from Recall when capture sync has completed.
        </p>
      </section>

      {decisiveArtifacts.length === 0 ? null : (
        <section aria-label="Decisive resources" style={styles.section}>
          <h2 style={styles.sectionTitle}>Decisive resources</h2>
          <ol style={styles.artifactList}>
            {decisiveArtifacts.map((artifact) => (
              <li key={artifact.id} style={styles.artifactItem}>
                <span style={styles.artifactTitle}>{artifact.title ?? artifact.hostname}</span>
                <span style={styles.meta}>{artifact.hostname}</span>
              </li>
            ))}
          </ol>
        </section>
      )}
    </article>
  );
}

function summaryMeta(session: ActiveProblemSessionPayload): string {
  const started = new Date(session.startedAt);
  if (session.endedAt === null) {
    return started.toLocaleDateString();
  }

  const durationMinutes = Math.max(1, Math.round((Date.parse(session.endedAt) - started.getTime()) / 60_000));
  return `${started.toLocaleDateString()} • ${durationMinutes} min`;
}

function statusCopy(status: "editing" | "error" | "loading" | ProblemSummarySaveStatus): string {
  switch (status) {
    case "editing":
      return "Editing your user-authored solution.";
    case "error":
      return "Some matching details are unavailable. Retry saving when Trail is online.";
    case "loading":
      return "Saving...";
    case "offline":
      return "Saved on this device. Trail will sync when online.";
    case "saved":
      return "Saved.";
  }
}

function buttonStyle(variant: "ghost" | "primary" | "secondary"): CSSProperties {
  const base: CSSProperties = {
    borderRadius: "10px",
    cursor: "pointer",
    fontSize: "13px",
    fontWeight: 650,
    padding: "9px 11px"
  };

  if (variant === "primary") {
    return { ...base, background: "#62D49C", border: "1px solid #7BE4B0", color: "#07100B" };
  }
  if (variant === "secondary") {
    return { ...base, background: "#181C22", border: "1px solid #303744", color: "#F4F7FB" };
  }
  return { ...base, background: "transparent", border: "1px solid #303744", color: "#AEB8C5" };
}

const styles = {
  actions: {
    display: "flex",
    gap: "8px",
    marginTop: "10px"
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
  authorLabel: {
    color: "#707987",
    fontSize: "12px"
  },
  error: {
    color: "#F0A1A8",
    fontSize: "12px",
    margin: "10px 0 0"
  },
  eyebrow: {
    color: "#62D49C",
    fontSize: "11px",
    fontWeight: 700,
    letterSpacing: "0.02em",
    margin: 0,
    textTransform: "uppercase"
  },
  header: {
    display: "grid",
    gap: "6px"
  },
  meta: {
    color: "#707987",
    fontSize: "12px",
    margin: "8px 0 0"
  },
  section: {
    borderTop: "1px solid #262B33",
    paddingTop: "14px"
  },
  sectionTitle: {
    color: "#F4F7FB",
    fontSize: "14px",
    margin: "0 0 8px"
  },
  shell: {
    display: "grid",
    gap: "14px"
  },
  skeletonLine: {
    background: "#262B33",
    borderRadius: "6px",
    display: "block",
    height: "12px",
    width: "100%"
  },
  skeletonLineShort: {
    background: "#262B33",
    borderRadius: "6px",
    display: "block",
    height: "12px",
    width: "62%"
  },
  skeletonStack: {
    display: "grid",
    gap: "8px"
  },
  solutionCard: {
    background: "#0B1510",
    border: "1px solid #255C3D",
    borderRadius: "14px",
    padding: "12px"
  },
  solutionHeader: {
    alignItems: "center",
    display: "flex",
    justifyContent: "space-between"
  },
  solutionLabel: {
    color: "#A8F0C6",
    fontSize: "12px",
    fontWeight: 750
  },
  solutionText: {
    color: "#F4F7FB",
    fontSize: "15px",
    lineHeight: 1.55,
    margin: "10px 0"
  },
  textarea: {
    background: "#07100B",
    border: "1px solid #3B8058",
    borderRadius: "10px",
    color: "#F4F7FB",
    font: "inherit",
    lineHeight: 1.5,
    marginTop: "10px",
    minHeight: "110px",
    padding: "10px",
    resize: "vertical",
    width: "100%"
  },
  title: {
    color: "#F4F7FB",
    fontSize: "20px",
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
