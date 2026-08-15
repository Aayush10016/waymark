import type { CSSProperties, ReactElement } from "react";
import { useMemo, useState } from "react";
import type { GhostReplayResourcePayload, SimilarProblemRecommendationPayload } from "@trail/extension-messages";
import { initialGhostReplayProgress, markGhostReplayResourceOpened, selectNextGhostReplayResource } from "./ghost-replay-progress";

export interface GhostReplayPanelProps {
  readonly onBackToSolution: () => void;
  readonly onFullTrail: () => void;
  readonly onOpenResource: (resourceId: string) => Promise<boolean>;
  readonly recommendation: SimilarProblemRecommendationPayload;
}

export function GhostReplayPanel(props: GhostReplayPanelProps): ReactElement {
  const initialProgress = useMemo(
    () => initialGhostReplayProgress(props.recommendation.resources ?? []),
    [props.recommendation.resources]
  );
  const [progress, setProgress] = useState(initialProgress);
  const [openErrorResourceId, setOpenErrorResourceId] = useState<string | null>(null);
  const activeResource =
    progress.activeResourceId === null
      ? null
      : progress.resources.find((resource) => resource.id === progress.activeResourceId) ?? null;
  const primaryLabel = primaryActionLabel(activeResource, progress.resources.some((resource) => resource.openedNow));

  async function openActiveResource(): Promise<void> {
    if (activeResource === null) {
      props.onBackToSolution();
      return;
    }

    const opened = await props.onOpenResource(activeResource.id);
    if (!opened) {
      setOpenErrorResourceId(activeResource.id);
      return;
    }

    setOpenErrorResourceId(null);
    setProgress((current) => markGhostReplayResourceOpened(current, activeResource.id));
  }

  return (
    <section
      aria-labelledby="ghost-replay-title"
      className="trail-ghost-replay-panel"
      data-layout="left-right narrow-aware"
      data-motion="prefers-reduced-motion-safe"
      style={styles.panel}
    >
      <style>{ghostReplayStyles}</style>
      <header className="trail-ghost-replay-panel__header" style={styles.header}>
        <div>
          <p style={styles.eyebrow}>Ghost Replay</p>
          <h1 id="ghost-replay-title" style={styles.title}>
            {props.recommendation.title ?? "Previous solved problem"}
          </h1>
        </div>
        <button className="trail-ghost-replay-focusable" onClick={props.onBackToSolution} style={styles.secondaryButton} type="button">
          Back to solution
        </button>
      </header>
      <div className="trail-ghost-replay-panel__paths" style={styles.paths}>
        <section aria-labelledby="current-path-heading" className="trail-ghost-replay-panel__current" style={styles.pathColumn}>
          <p style={styles.pathLabel}>Current path</p>
          <h2 id="current-path-heading" style={styles.pathTitle}>Now</h2>
          <p style={styles.pathBody}>Trail keeps your current debugging flow in place.</p>
        </section>
        <section aria-labelledby="previous-path-heading" className="trail-ghost-replay-panel__previous" style={styles.pathColumn}>
          <p style={styles.pathLabel}>Previous path</p>
          <h2 id="previous-path-heading" style={styles.pathTitle}>Then</h2>
          <p style={styles.pathBody}>{props.recommendation.finalSolution}</p>
        </section>
      </div>
      <section aria-labelledby="ghost-replay-resources-heading" style={styles.resourcesSection}>
        <div style={styles.resourcesHeader}>
          <div>
            <p style={styles.pathLabel}>Key resources</p>
            <h2 id="ghost-replay-resources-heading" style={styles.pathTitle}>Replay one resource at a time</h2>
          </div>
          <button
            className="trail-ghost-replay-focusable"
            disabled={activeResource === null}
            onClick={() => {
              void openActiveResource();
            }}
            style={activeResource === null ? styles.disabledButton : styles.primaryButton}
            type="button"
          >
            {primaryLabel}
          </button>
        </div>
        {progress.resources.length === 0 ? (
          <p style={styles.pathBody}>The previous resources are unavailable; your saved solution is intact.</p>
        ) : (
          <ol aria-label="Ghost Replay resources" style={styles.resourceList}>
            {progress.resources.map((resource) => (
              <li key={resource.id} style={resource.id === progress.activeResourceId ? styles.activeResourceItem : styles.resourceItem}>
                <ResourceRow error={openErrorResourceId === resource.id} resource={resource} />
              </li>
            ))}
          </ol>
        )}
      </section>
      <footer style={styles.footer}>
        <button className="trail-ghost-replay-focusable" onClick={props.onFullTrail} style={styles.secondaryButton} type="button">
          Full trail
        </button>
        <button
          className="trail-ghost-replay-focusable"
          disabled={progress.activeResourceId === null}
          onClick={() => setProgress(selectNextGhostReplayResource(progress))}
          style={progress.activeResourceId === null ? styles.disabledButton : styles.secondaryButton}
          type="button"
        >
          Open next
        </button>
        <button className="trail-ghost-replay-focusable" onClick={props.onBackToSolution} style={styles.primaryButton} type="button">
          Return to current problem
        </button>
      </footer>
    </section>
  );
}

function ResourceRow(props: { readonly error: boolean; readonly resource: GhostReplayResourcePayload }): ReactElement {
  const isOpenable = props.resource.url !== null && props.resource.url !== undefined;

  return (
    <article style={styles.resourceRow}>
      <div style={styles.resourceText}>
        <p style={styles.resourceTitle}>{resourceTitle(props.resource)}</p>
        <p style={styles.resourceMeta}>{resourceMeta(props.resource)}</p>
      </div>
      <div style={styles.badges}>
        {props.resource.decisive ? <span style={styles.decisiveBadge}>Decisive resource</span> : null}
        {props.resource.openedNow ? <span style={styles.openedBadge}>Opened now</span> : null}
        {props.resource.alreadyVisited ? <span style={styles.alreadyBadge}>Already visited in this session</span> : null}
        {!isOpenable ? <span style={styles.disabledBadge}>Can&apos;t open this URL safely</span> : null}
        {props.error ? <span style={styles.errorBadge}>Chrome blocked the tab. Open again</span> : null}
      </div>
    </article>
  );
}

function resourceTitle(resource: GhostReplayResourcePayload): string {
  return resource.title ?? resource.queryText ?? resource.hostname ?? "Saved resource";
}

function resourceMeta(resource: GhostReplayResourcePayload): string {
  if (resource.hostname !== null && resource.hostname !== undefined) {
    return `${resource.kind.replaceAll("_", " ").toLowerCase()} · ${resource.hostname}`;
  }
  return resource.kind.replaceAll("_", " ").toLowerCase();
}

function primaryActionLabel(resource: GhostReplayResourcePayload | null, hasOpenedResource: boolean): string {
  if (resource === null) {
    return "Return to current problem";
  }
  if (hasOpenedResource) {
    return "Open next resource";
  }
  return resource.decisive ? "Open decisive resource" : "Open first resource";
}

export const ghostReplayStyles = `
.trail-ghost-replay-panel {
  animation: trailGhostReplayEnter 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.trail-ghost-replay-panel__current {
  opacity: 0.7;
  transform: translateX(-6px);
}

.trail-ghost-replay-panel__previous {
  animation: trailGhostPathAlign 320ms cubic-bezier(0.2, 0.8, 0.2, 1) 70ms both;
}

.trail-ghost-replay-focusable:focus-visible {
  outline: 2px solid #A79DFF;
  outline-offset: 3px;
}

@keyframes trailGhostReplayEnter {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes trailGhostPathAlign {
  from {
    opacity: 0.35;
    transform: translateX(18px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .trail-ghost-replay-panel,
  .trail-ghost-replay-panel__previous {
    animation: none;
    transform: none;
  }
}

@media (max-width: 420px) {
  .trail-ghost-replay-panel__paths {
    grid-template-columns: 1fr !important;
  }
}
`;

const styles = {
  eyebrow: {
    color: "#8B7CFF",
    fontSize: "11px",
    fontWeight: 800,
    letterSpacing: "0.04em",
    margin: 0,
    textTransform: "uppercase"
  },
  footer: {
    display: "flex",
    gap: "10px",
    justifyContent: "flex-end"
  },
  header: {
    alignItems: "start",
    display: "flex",
    gap: "12px",
    justifyContent: "space-between"
  },
  panel: {
    background: "linear-gradient(180deg, #11141A 0%, #0B0D10 100%)",
    border: "1px solid #303744",
    borderRadius: "18px",
    color: "#F4F7FB",
    display: "grid",
    gap: "18px",
    padding: "18px"
  },
  pathBody: {
    color: "#D7DEE8",
    fontSize: "13px",
    lineHeight: 1.5,
    margin: "8px 0 0"
  },
  pathColumn: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "16px",
    minWidth: 0,
    padding: "14px"
  },
  pathLabel: {
    color: "#7D8794",
    fontSize: "11px",
    fontWeight: 750,
    margin: 0
  },
  pathTitle: {
    color: "#F4F7FB",
    fontSize: "16px",
    margin: "6px 0 0"
  },
  activeResourceItem: {
    background: "linear-gradient(90deg, rgba(139, 124, 255, 0.14), rgba(139, 124, 255, 0))",
    border: "1px solid #8B7CFF",
    borderRadius: "14px",
    padding: "10px"
  },
  alreadyBadge: {
    background: "#182232",
    border: "1px solid #31527A",
    borderRadius: "999px",
    color: "#A9C8FF",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  badges: {
    display: "flex",
    flexWrap: "wrap",
    gap: "6px",
    justifyContent: "flex-end"
  },
  decisiveBadge: {
    background: "#1F1B35",
    border: "1px solid #8B7CFF",
    borderRadius: "999px",
    color: "#C9C3FF",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  disabledBadge: {
    background: "#221D1D",
    border: "1px solid #5B3333",
    borderRadius: "999px",
    color: "#FFB4B4",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  disabledButton: {
    background: "#181C22",
    border: "1px solid #303744",
    borderRadius: "12px",
    color: "#7D8794",
    cursor: "not-allowed",
    fontSize: "13px",
    fontWeight: 750,
    padding: "10px 12px"
  },
  errorBadge: {
    background: "#2A1B12",
    border: "1px solid #7A4422",
    borderRadius: "999px",
    color: "#FFC49B",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  openedBadge: {
    background: "#171F19",
    border: "1px solid #3D7A4A",
    borderRadius: "999px",
    color: "#A8F0B8",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  paths: {
    display: "grid",
    gap: "12px",
    gridTemplateColumns: "0.85fr 1.15fr"
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
  resourceItem: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "14px",
    padding: "10px"
  },
  resourceList: {
    display: "grid",
    gap: "8px",
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  resourceMeta: {
    color: "#7D8794",
    fontSize: "12px",
    margin: "3px 0 0",
    textTransform: "capitalize"
  },
  resourceRow: {
    alignItems: "center",
    display: "grid",
    gap: "10px",
    gridTemplateColumns: "1fr auto"
  },
  resourcesHeader: {
    alignItems: "center",
    display: "flex",
    gap: "12px",
    justifyContent: "space-between"
  },
  resourcesSection: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "16px",
    display: "grid",
    gap: "12px",
    padding: "14px"
  },
  resourceText: {
    minWidth: 0
  },
  resourceTitle: {
    color: "#F4F7FB",
    fontSize: "13px",
    fontWeight: 750,
    margin: 0
  },
  secondaryButton: {
    background: "transparent",
    border: "1px solid #303744",
    borderRadius: "12px",
    color: "#D7DEE8",
    cursor: "pointer",
    fontSize: "12px",
    fontWeight: 700,
    padding: "8px 10px"
  },
  title: {
    fontSize: "18px",
    letterSpacing: "-0.04em",
    margin: "4px 0 0"
  }
} satisfies Record<string, CSSProperties>;
