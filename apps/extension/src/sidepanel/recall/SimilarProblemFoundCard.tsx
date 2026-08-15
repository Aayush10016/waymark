import type { CSSProperties, ReactElement } from "react";
import type { SimilarProblemRecommendationPayload } from "@trail/extension-messages";

export interface SimilarProblemFoundCardProps {
  readonly now?: Date;
  readonly onReplay: () => void;
  readonly recommendation: SimilarProblemRecommendationPayload;
}

export function SimilarProblemFoundCard(props: SimilarProblemFoundCardProps): ReactElement {
  const now = props.now ?? new Date();
  const evidence = props.recommendation.evidence.slice(0, 3);

  return (
    <article aria-labelledby="similar-problem-title" style={styles.card}>
      <p style={styles.eyebrow}>Similar Problem Found</p>
      <h1 id="similar-problem-title" style={styles.title}>
        {props.recommendation.title ?? "You solved something similar before."}
      </h1>
      <dl aria-label="Prior solution metadata" style={styles.metaGrid}>
        <div>
          <dt style={styles.metaLabel}>Solved</dt>
          <dd style={styles.metaValue}>{ageLabel(props.recommendation.solvedAt ?? null, now)}</dd>
        </div>
        <div>
          <dt style={styles.metaLabel}>Duration</dt>
          <dd style={styles.metaValue}>{durationLabel(props.recommendation.durationSeconds ?? null)}</dd>
        </div>
      </dl>
      <section aria-labelledby="previous-solution-heading" style={styles.section}>
        <h2 id="previous-solution-heading" style={styles.sectionTitle}>Previous solution</h2>
        <p style={styles.solution}>{props.recommendation.finalSolution}</p>
      </section>
      <section aria-labelledby="evidence-heading" style={styles.section}>
        <h2 id="evidence-heading" style={styles.sectionTitle}>Evidence</h2>
        <ul style={styles.evidenceList}>
          {evidence.map((item) => (
            <li key={`${item.evidenceCode}:${item.problemSessionId}:${item.artifactId ?? "session"}`} style={styles.evidenceItem}>
              {evidenceLabel(item.evidenceCode)}
            </li>
          ))}
        </ul>
      </section>
      <section aria-labelledby="why-this-heading" style={styles.section}>
        <h2 id="why-this-heading" style={styles.sectionTitle}>Why this?</h2>
        <ul style={styles.whyList}>
          {props.recommendation.whyThis.map((item) => (
            <li key={`${item.evidenceCode}:${item.problemSessionId}:${item.artifactId ?? "session"}:${item.message}`}>
              {item.message}
            </li>
          ))}
        </ul>
      </section>
      <footer style={styles.actions}>
        <button onClick={props.onReplay} style={styles.primaryButton} type="button">
          Replay solution
        </button>
      </footer>
    </article>
  );
}

export function ageLabel(solvedAt: string | null, now: Date): string {
  if (solvedAt === null) {
    return "Previously solved";
  }

  const solved = Date.parse(solvedAt);
  if (Number.isNaN(solved)) {
    return "Previously solved";
  }

  const days = Math.max(0, Math.floor((now.getTime() - solved) / 86_400_000));
  if (days === 0) {
    return "today";
  }
  if (days === 1) {
    return "1 day ago";
  }
  return `${days} days ago`;
}

export function durationLabel(durationSeconds: number | null): string {
  if (durationSeconds === null) {
    return "Duration unknown";
  }

  const hours = Math.floor(durationSeconds / 3600);
  const minutes = Math.floor((durationSeconds % 3600) / 60);
  if (hours > 0 && minutes > 0) {
    return `${hours}h ${minutes}m`;
  }
  if (hours > 0) {
    return `${hours}h`;
  }
  return `${minutes}m`;
}

function evidenceLabel(evidenceCode: SimilarProblemRecommendationPayload["evidence"][number]["evidenceCode"]): string {
  switch (evidenceCode) {
    case "ERROR_PHRASE":
      return "Same error phrase";
    case "LEXICAL_TEXT":
      return "Similar problem text";
    case "TECHNOLOGY":
      return "Shared technology";
    case "VECTOR_DISTANCE":
      return "Similar debugging context";
  }
}

const styles = {
  actions: {
    display: "flex",
    justifyContent: "flex-start"
  },
  card: {
    background: "#11141A",
    border: "1px solid #303744",
    borderRadius: "18px",
    boxShadow: "0 18px 60px rgba(0, 0, 0, 0.32)",
    color: "#F4F7FB",
    display: "grid",
    gap: "16px",
    padding: "18px"
  },
  evidenceItem: {
    background: "#181C22",
    border: "1px solid #303744",
    borderRadius: "999px",
    color: "#D7DEE8",
    fontSize: "12px",
    padding: "7px 9px"
  },
  evidenceList: {
    display: "flex",
    flexWrap: "wrap",
    gap: "8px",
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  eyebrow: {
    color: "#8B7CFF",
    fontSize: "11px",
    fontWeight: 800,
    letterSpacing: "0.04em",
    margin: 0,
    textTransform: "uppercase"
  },
  metaGrid: {
    display: "grid",
    gap: "10px",
    gridTemplateColumns: "1fr 1fr",
    margin: 0
  },
  metaLabel: {
    color: "#7D8794",
    fontSize: "11px",
    margin: 0
  },
  metaValue: {
    color: "#F4F7FB",
    fontSize: "13px",
    fontWeight: 700,
    margin: "4px 0 0"
  },
  primaryButton: {
    background: "#8B7CFF",
    border: "1px solid #A79DFF",
    borderRadius: "12px",
    color: "#07080A",
    cursor: "pointer",
    fontSize: "13px",
    fontWeight: 750,
    padding: "10px 12px"
  },
  section: {
    display: "grid",
    gap: "8px"
  },
  sectionTitle: {
    color: "#AEB8C5",
    fontSize: "12px",
    fontWeight: 750,
    margin: 0
  },
  solution: {
    color: "#F4F7FB",
    fontSize: "14px",
    lineHeight: 1.55,
    margin: 0
  },
  title: {
    fontSize: "20px",
    letterSpacing: "-0.04em",
    margin: 0
  },
  whyList: {
    color: "#D7DEE8",
    display: "grid",
    fontSize: "13px",
    gap: "8px",
    lineHeight: 1.5,
    margin: 0,
    paddingLeft: "18px"
  }
} satisfies Record<string, CSSProperties>;
