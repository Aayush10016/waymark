import type { CSSProperties, KeyboardEvent, ReactElement } from "react";
import { useState } from "react";
import {
  groupTimelineRows,
  nextTimelineSelection,
  removeTimelineRow,
  restoreTimelineRow,
  type TimelineMode,
  type TimelineRow
} from "./timeline-model";

export interface TimelinePanelProps {
  readonly mode: TimelineMode;
  readonly onBack: () => void;
  readonly onOpenRow?: (rowId: string) => void;
  readonly problemTitle: string;
  readonly rows: readonly TimelineRow[];
}

export function TimelinePanel(props: TimelinePanelProps): ReactElement {
  const [rows, setRows] = useState(props.rows);
  const [removedRow, setRemovedRow] = useState<TimelineRow | null>(null);
  const groupedRows = groupTimelineRows(rows);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(groupedRows.length === 0 ? null : 0);

  function handleKeyDown(event: KeyboardEvent<HTMLElement>): void {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelectedIndex((current) => nextTimelineSelection(current, groupedRows.length, 1));
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelectedIndex((current) => nextTimelineSelection(current, groupedRows.length, -1));
    }
    if (event.key === "Enter" && selectedIndex !== null) {
      props.onOpenRow?.(groupedRows[selectedIndex]?.row.id ?? "");
    }
  }

  function removeRow(row: TimelineRow): void {
    setRows((current) => removeTimelineRow(current, row.id));
    setRemovedRow(row);
  }

  function undoRemove(): void {
    if (removedRow === null) {
      return;
    }
    setRows((current) => restoreTimelineRow(current, removedRow));
    setRemovedRow(null);
  }

  return (
    <section
      aria-labelledby="timeline-title"
      data-mode={props.mode}
      onKeyDown={handleKeyDown}
      style={styles.panel}
      tabIndex={0}
    >
      <header style={styles.header}>
        <div>
          <p style={styles.eyebrow}>{modeLabel(props.mode)}</p>
          <h1 id="timeline-title" style={styles.title}>{props.problemTitle}</h1>
        </div>
        <button onClick={props.onBack} style={styles.secondaryButton} type="button">Back to solution</button>
      </header>

      <section aria-label="Solution anchor" style={styles.solutionAnchor}>
        <p style={styles.solutionLabel}>User-authored solution remains primary</p>
      </section>

      {removedRow !== null ? (
        <div role="status" style={styles.undoRow}>
          Removed one event.
          <button onClick={undoRemove} style={styles.inlineButton} type="button">Undo</button>
        </div>
      ) : null}

      {groupedRows.length === 0 ? (
        <p style={styles.empty}>No browsing context was saved for this problem.</p>
      ) : (
        <ol aria-label="Problem timeline" style={styles.list}>
          {groupedRows.map((item, index) => (
            <li
              aria-selected={selectedIndex === index}
              key={item.row.id}
              style={selectedIndex === index ? styles.selectedRow : styles.row}
            >
              <article style={styles.rowContent}>
                <div>
                  <p style={styles.rowTitle}>{rowTitle(item.row)}</p>
                  <p style={styles.meta}>
                    {new Date(item.row.occurredAt).toLocaleTimeString()} · {item.row.type.replaceAll("_", " ").toLowerCase()}
                    {item.row.hostname ? ` · ${item.row.hostname}` : ""}
                  </p>
                </div>
                <div style={styles.badges}>
                  {item.row.decisive ? <span style={styles.decisiveBadge}>Decisive</span> : null}
                  {props.mode === "ghost" && item.row.openedNow ? <span style={styles.openedBadge}>Opened now</span> : null}
                  {item.repeatCount > 0 ? <span style={styles.repeatBadge}>+{item.repeatCount} repeats</span> : null}
                </div>
                {props.mode === "candidate" ? (
                  <button onClick={() => removeRow(item.row)} style={styles.secondaryButton} type="button">Remove</button>
                ) : null}
              </article>
            </li>
          ))}
        </ol>
      )}

      <p style={styles.shortcutHelp}>Use ↑/↓ to move through rows, Enter to open, D to mark decisive when available.</p>
    </section>
  );
}

function rowTitle(row: TimelineRow): string {
  return row.title ?? row.queryText ?? row.url ?? "Saved event";
}

function modeLabel(mode: TimelineMode): string {
  switch (mode) {
    case "candidate":
      return "Candidate review timeline";
    case "ghost":
      return "Ghost Replay timeline";
    case "historical":
      return "Historical timeline";
  }
}

const styles = {
  badges: {
    display: "flex",
    flexWrap: "wrap",
    gap: "6px"
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
  empty: {
    color: "#AEB8C5",
    fontSize: "13px",
    margin: 0
  },
  eyebrow: {
    color: "#8B7CFF",
    fontSize: "11px",
    fontWeight: 800,
    letterSpacing: "0.04em",
    margin: 0,
    textTransform: "uppercase"
  },
  header: {
    alignItems: "start",
    display: "flex",
    gap: "12px",
    justifyContent: "space-between"
  },
  inlineButton: {
    background: "transparent",
    border: 0,
    color: "#A79DFF",
    cursor: "pointer",
    fontWeight: 750,
    padding: "0 0 0 8px"
  },
  list: {
    display: "grid",
    gap: "8px",
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  meta: {
    color: "#7D8794",
    fontSize: "12px",
    margin: "4px 0 0",
    textTransform: "capitalize"
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
  panel: {
    background: "linear-gradient(180deg, #11141A 0%, #0B0D10 100%)",
    border: "1px solid #303744",
    borderRadius: "18px",
    color: "#F4F7FB",
    display: "grid",
    gap: "14px",
    outline: "none",
    padding: "18px"
  },
  repeatBadge: {
    background: "#181C22",
    border: "1px solid #303744",
    borderRadius: "999px",
    color: "#D7DEE8",
    fontSize: "11px",
    fontWeight: 750,
    padding: "5px 7px"
  },
  row: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "14px",
    padding: "10px"
  },
  rowContent: {
    alignItems: "center",
    display: "grid",
    gap: "10px",
    gridTemplateColumns: "1fr auto auto"
  },
  rowTitle: {
    color: "#F4F7FB",
    fontSize: "13px",
    fontWeight: 750,
    margin: 0
  },
  secondaryButton: {
    background: "transparent",
    border: "1px solid #303744",
    borderRadius: "10px",
    color: "#D7DEE8",
    cursor: "pointer",
    fontSize: "12px",
    fontWeight: 700,
    padding: "8px 10px"
  },
  selectedRow: {
    background: "linear-gradient(90deg, rgba(139, 124, 255, 0.14), rgba(139, 124, 255, 0))",
    border: "1px solid #8B7CFF",
    borderRadius: "14px",
    padding: "10px"
  },
  shortcutHelp: {
    color: "#7D8794",
    fontSize: "12px",
    margin: 0
  },
  solutionAnchor: {
    background: "#0B0D10",
    border: "1px solid #262B33",
    borderRadius: "14px",
    padding: "10px"
  },
  solutionLabel: {
    color: "#D7DEE8",
    fontSize: "13px",
    margin: 0
  },
  title: {
    fontSize: "18px",
    letterSpacing: "-0.04em",
    margin: "4px 0 0"
  },
  undoRow: {
    background: "#181C22",
    border: "1px solid #303744",
    borderRadius: "12px",
    color: "#D7DEE8",
    fontSize: "12px",
    padding: "9px 10px"
  }
} satisfies Record<string, CSSProperties>;
