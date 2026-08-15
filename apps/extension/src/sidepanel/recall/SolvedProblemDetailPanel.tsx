import type { CSSProperties, ReactElement } from "react";
import type { GhostReplayResourcePayload } from "@trail/extension-messages";
import { trailColors, trailFocusRing, trailRadii, trailSpacing, trailTypography } from "@trail/ui";

export interface SolvedProblemDetailPanelProps {
  readonly finalSolution: string;
  readonly onFullTrail: () => void;
  readonly onOpenResource: (resourceId: string) => Promise<boolean>;
  readonly onReplay: () => void;
  readonly resources: readonly GhostReplayResourcePayload[];
  readonly title?: string | null;
  readonly updatedAt: string;
}

/**
 * Deterministic saved Problem Session detail rendered from persisted session artifacts.
 */
export function SolvedProblemDetailPanel(props: SolvedProblemDetailPanelProps): ReactElement {
  return (
    <section aria-labelledby="solved-problem-title" style={styles.panel}>
      <style>{detailCss}</style>
      <header style={styles.header}>
        <p style={styles.eyebrow}>Saved to your engineering memory</p>
        <h1 id="solved-problem-title" style={styles.title}>{props.title ?? "Solved problem"}</h1>
        <p style={styles.meta}>{new Date(props.updatedAt).toLocaleDateString()}</p>
      </header>

      <section aria-label="Your solution" style={styles.solutionCard}>
        <p style={styles.sectionLabel}>Your solution</p>
        <p style={styles.solution}>{props.finalSolution}</p>
      </section>

      <section aria-labelledby="saved-trail-title" style={styles.resourcesSection}>
        <div style={styles.resourcesHeader}>
          <div>
            <p style={styles.sectionLabel}>Trail</p>
            <h2 id="saved-trail-title" style={styles.sectionTitle}>
              {props.resources.length === 1 ? "1 resource remembered" : `${props.resources.length} resources remembered`}
            </h2>
          </div>
          <div style={styles.actions}>
            <button className="trail-solved-detail-button" disabled={props.resources.length === 0} onClick={props.onReplay} style={props.resources.length === 0 ? styles.disabledButton : styles.secondaryButton} type="button">
              Replay trail
            </button>
            <button className="trail-solved-detail-button" disabled={props.resources.length === 0} onClick={props.onFullTrail} style={props.resources.length === 0 ? styles.disabledButton : styles.secondaryButton} type="button">
              Full trail
            </button>
          </div>
        </div>
        {props.resources.length === 0 ? (
          <p style={styles.body}>No resources are attached to this solved problem yet. The solution remains searchable.</p>
        ) : (
          <ol aria-label="Saved trail resources" style={styles.resourceList}>
            {props.resources.map((resource) => (
              <li key={resource.id} style={styles.resourceItem}>
                <ResourceRow onOpen={props.onOpenResource} resource={resource} />
              </li>
            ))}
          </ol>
        )}
      </section>
    </section>
  );
}

function ResourceRow(props: {
  readonly onOpen: (resourceId: string) => Promise<boolean>;
  readonly resource: GhostReplayResourcePayload;
}): ReactElement {
  const openable = props.resource.url !== null && props.resource.url !== undefined;

  return (
    <article style={styles.resourceRow}>
      <div>
        <p style={styles.resourceTitle}>{resourceTitle(props.resource)}</p>
        <p style={styles.resourceMeta}>{resourceMeta(props.resource)}</p>
      </div>
      <button
        className="trail-solved-detail-button"
        disabled={!openable}
        onClick={() => {
          void props.onOpen(props.resource.id);
        }}
        style={openable ? styles.primaryButton : styles.disabledButton}
        type="button"
      >
        {props.resource.openedNow ? "Opened" : "Open page"}
      </button>
    </article>
  );
}

function resourceTitle(resource: GhostReplayResourcePayload): string {
  return resource.title ?? resource.queryText ?? resource.hostname ?? "Saved resource";
}

function resourceMeta(resource: GhostReplayResourcePayload): string {
  const kind = resource.kind.replaceAll("_", " ").toLowerCase();
  const visited = `Visited ${new Date(resource.occurredAt).toLocaleString()}`;
  if (resource.hostname !== null && resource.hostname !== undefined) {
    return `${kind} · ${resource.hostname} · ${visited}`;
  }
  return `${kind} · ${visited}`;
}

const detailCss = `
  .trail-solved-detail-button:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }
`;

const styles = {
  actions: {
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.sm
  },
  body: {
    color: trailColors.textMuted,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: 0
  },
  disabledButton: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.button,
    color: trailColors.muted,
    cursor: "not-allowed",
    fontSize: trailTypography.control,
    fontWeight: 750,
    padding: "8px 10px"
  },
  eyebrow: {
    color: trailColors.success,
    fontSize: trailTypography.caption,
    fontWeight: 850,
    letterSpacing: "0.08em",
    margin: 0,
    textTransform: "uppercase"
  },
  header: {
    display: "grid",
    gap: trailSpacing.sm
  },
  meta: {
    color: trailColors.muted,
    fontSize: trailTypography.control,
    margin: 0
  },
  panel: {
    background: trailColors.background,
    color: trailColors.text,
    display: "grid",
    fontFamily: trailTypography.family,
    gap: trailSpacing.lg
  },
  primaryButton: {
    background: trailColors.accent,
    border: `1px solid ${trailColors.accentStrong}`,
    borderRadius: trailRadii.button,
    color: "#07080A",
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 800,
    padding: "8px 10px"
  },
  resourceItem: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.field,
    padding: trailSpacing.md
  },
  resourceList: {
    display: "grid",
    gap: trailSpacing.sm,
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  resourceMeta: {
    color: trailColors.muted,
    fontSize: trailTypography.control,
    margin: "5px 0 0"
  },
  resourceRow: {
    alignItems: "center",
    display: "flex",
    gap: trailSpacing.md,
    justifyContent: "space-between"
  },
  resourceTitle: {
    color: trailColors.text,
    fontSize: trailTypography.body,
    fontWeight: 750,
    margin: 0
  },
  resourcesHeader: {
    display: "grid",
    gap: trailSpacing.md
  },
  resourcesSection: {
    display: "grid",
    gap: trailSpacing.md
  },
  secondaryButton: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.button,
    color: trailColors.textSubtle,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 750,
    padding: "8px 10px"
  },
  sectionLabel: {
    color: trailColors.textMuted,
    fontSize: trailTypography.caption,
    fontWeight: 800,
    margin: 0,
    textTransform: "uppercase"
  },
  sectionTitle: {
    color: trailColors.text,
    fontSize: "16px",
    margin: "4px 0 0"
  },
  solution: {
    color: trailColors.textSubtle,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: "6px 0 0"
  },
  solutionCard: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.field,
    padding: trailSpacing.lg
  },
  title: {
    color: trailColors.text,
    fontSize: "21px",
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;


