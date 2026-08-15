import type { CSSProperties, KeyboardEvent, ReactElement } from "react";
import { useEffect, useState } from "react";
import type { RecallSearchResultPayload, RecallSearchResultPayloadEnvelope } from "@trail/extension-messages";
import { trailColors, trailFocusRing, trailRadii, trailSpacing, trailTypography } from "@trail/ui";

export interface RecallSearchPanelProps {
  readonly currentContextTitle?: string | null;
  readonly initialQuery?: string;
  readonly initialSearchResult?: RecallSearchResultPayloadEnvelope;
  readonly onOpenResult: (result: RecallSearchResultPayload) => Promise<void>;
  readonly onSearch: (query: string | null) => Promise<RecallSearchResultPayloadEnvelope>;
  readonly surface: "full-page" | "side-panel";
}

export function RecallSearchPanel(props: RecallSearchPanelProps): ReactElement {
  const [query, setQuery] = useState(props.initialQuery ?? "");
  const [currentContextVisible, setCurrentContextVisible] = useState(Boolean(props.currentContextTitle));
  const [result, setResult] = useState<RecallSearchResultPayloadEnvelope>(
    props.initialSearchResult ?? {
      degradationMode: null,
      query: null,
      results: [],
      status: "received"
    }
  );
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    const timeout = setTimeout(() => {
      setLoading(true);
      void props
        .onSearch(query.trim().length === 0 ? null : query)
        .then((nextResult) => {
          if (!active) {
            return;
          }
          setResult(nextResult);
          setSelectedIndex(nextResult.results.length === 0 ? null : 0);
        })
        .catch(() => {
          if (active) {
            setResult({
              degradationMode: null,
              query,
              results: [],
              status: "unavailable"
            });
          }
        })
        .finally(() => {
          if (active) {
            setLoading(false);
          }
        });
    }, 150);

    return () => {
      active = false;
      clearTimeout(timeout);
    };
  }, [props.onSearch, query]);

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>): void {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelectedIndex((current) => nextIndex(current, result.results.length, 1));
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelectedIndex((current) => nextIndex(current, result.results.length, -1));
    }
    if (event.key === "Escape" && selectedIndex !== null) {
      event.preventDefault();
      setSelectedIndex(null);
    }
    if (event.key === "Enter" && selectedIndex !== null) {
      const selectedResult = result.results[selectedIndex];
      if (selectedResult !== undefined) {
        event.preventDefault();
        void props.onOpenResult(selectedResult);
      }
    }
  }

  const hasQuery = query.trim().length > 0;
  const heading = hasQuery ? "Search your engineering memory" : "Recent solved problems";
  const selectedResultId = selectedIndex === null ? undefined : `recall-result-${selectedIndex}`;

  return (
    <section aria-labelledby="recall-search-title" data-surface={props.surface} style={styles.panel}>
      <style>{recallSearchCss}</style>
      <header style={styles.header}>
        <p style={styles.eyebrow}>Trail Recall</p>
        <h1 id="recall-search-title" style={styles.title}>{heading}</h1>
        <p style={styles.body}>Search solved problems you already saved. No chatbot, no web search.</p>
      </header>

      <label style={styles.searchLabel}>
        <span style={styles.searchLabelText}>Search</span>
        <input
          aria-activedescendant={selectedResultId}
          aria-controls="recall-search-results"
          aria-label="Search problems, errors, technologies, or solutions"
          className="trail-recall-field"
          onChange={(event) => setQuery(event.currentTarget.value.replace(/\s+/g, " "))}
          onKeyDown={handleKeyDown}
          placeholder="Search an error, framework, or fix…"
          style={styles.searchInput}
          value={query}
        />
      </label>

      {currentContextVisible && props.currentContextTitle ? (
        <button className="trail-recall-button" onClick={() => setCurrentContextVisible(false)} style={styles.currentChip} type="button">
          Current context · {props.currentContextTitle} ×
        </button>
      ) : null}

      {loading ? <div aria-label="Search loading" style={styles.progressLine} /> : null}
      {result.status === "authentication_required" ? (
        <p role="status" style={styles.error}>Reconnect Trail, then search your engineering memory.</p>
      ) : null}
      {result.status === "unavailable" ? (
        <p role="status" style={styles.error}>Search is temporarily unavailable. Your query is preserved.</p>
      ) : null}
      {result.status === "received" && result.degradationMode === "LEXICAL" ? (
        <p role="status" style={styles.degraded}>Semantic matching is temporarily unavailable.</p>
      ) : null}

      {result.results.length === 0 && hasQuery && result.status === "received" ? (
        <div style={styles.emptyState}>
          <p style={styles.body}>No solved problem matches “{query}”.</p>
          <p style={styles.body}>Try an exact error phrase, a technology, or fewer framework-version details.</p>
          <button className="trail-recall-button" onClick={() => setQuery("")} style={styles.secondaryButton} type="button">
            Clear search
          </button>
        </div>
      ) : (
        <ol
          aria-label={hasQuery ? "Search results" : "Recent solved problems"}
          id="recall-search-results"
          style={loading ? styles.dimmedResultList : styles.resultList}
        >
          {result.results.slice(0, hasQuery ? 20 : 5).map((item, index) => (
            <li
              aria-selected={selectedIndex === index}
              id={`recall-result-${index}`}
              key={item.problemSessionId}
              style={selectedIndex === index ? styles.selectedResultItem : styles.resultItem}
            >
              <SearchResultRow onOpen={() => void props.onOpenResult(item)} result={item} />
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function SearchResultRow(props: { readonly onOpen: () => void; readonly result: RecallSearchResultPayload }): ReactElement {
  return (
    <article style={styles.resultRow}>
      <div>
        <h2 style={styles.resultTitle}>{props.result.title ?? "Solved problem"}</h2>
        <p style={styles.solution}>{props.result.finalSolution}</p>
        <p style={styles.resultMeta}>{new Date(props.result.updatedAt).toLocaleDateString()} · {evidenceLabel(props.result)}</p>
      </div>
      <button className="trail-recall-button" onClick={props.onOpen} style={styles.primaryButton} type="button">
        View trail
      </button>
      <div style={styles.tags}>
        {props.result.technologies.slice(0, 3).map((technology) => (
          <span key={technology} style={styles.tag}>{technology}</span>
        ))}
      </div>
    </article>
  );
}

function nextIndex(current: number | null, length: number, direction: 1 | -1): number | null {
  if (length === 0) {
    return null;
  }
  if (current === null) {
    return direction === 1 ? 0 : length - 1;
  }
  return (current + direction + length) % length;
}

function evidenceLabel(result: RecallSearchResultPayload): string {
  if (result.evidenceCodes.length === 0) {
    return "Solved recently";
  }
  if (result.evidenceCodes.includes("ERROR_PHRASE")) {
    return "Matched exact error";
  }
  if (result.evidenceCodes.includes("TECHNOLOGY")) {
    return "Matched technology";
  }
  return "Matched solution text";
}

const recallSearchCss = `
  .trail-recall-field:focus-visible,
  .trail-recall-button:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }
  .trail-recall-button {
    transition: background 140ms ease, border-color 140ms ease, transform 140ms ease;
  }
  .trail-recall-button:hover:not(:disabled) {
    transform: translateY(-1px);
  }
  @media (prefers-reduced-motion: reduce) {
    .trail-recall-button {
      transition: none;
    }
    .trail-recall-button:hover:not(:disabled) {
      transform: none;
    }
  }
`;

const styles = {
  body: {
    color: trailColors.textMuted,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: 0
  },
  currentChip: {
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.pill,
    color: trailColors.textSubtle,
    cursor: "pointer",
    fontSize: trailTypography.control,
    justifySelf: "start",
    padding: "7px 10px"
  },
  degraded: {
    background: "rgba(139, 124, 255, 0.12)",
    border: "1px solid rgba(139, 124, 255, 0.42)",
    borderRadius: trailRadii.button,
    color: trailColors.accentMuted,
    fontSize: trailTypography.control,
    margin: 0,
    padding: "9px 10px"
  },
  dimmedResultList: {
    display: "grid",
    gap: trailSpacing.md,
    listStyle: "none",
    margin: 0,
    opacity: 0.5,
    padding: 0
  },
  emptyState: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.field,
    display: "grid",
    gap: trailSpacing.sm,
    padding: trailSpacing.lg
  },
  error: {
    background: trailColors.dangerSurface,
    border: "1px solid rgba(255, 180, 180, 0.28)",
    borderRadius: trailRadii.button,
    color: trailColors.danger,
    fontSize: trailTypography.control,
    margin: 0,
    padding: "9px 10px"
  },
  eyebrow: {
    color: trailColors.accentStrong,
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
  panel: {
    background: trailColors.background,
    color: trailColors.text,
    display: "grid",
    fontFamily: trailTypography.family,
    gap: trailSpacing.lg,
    minHeight: "100vh"
  },
  progressLine: {
    background: `linear-gradient(90deg, ${trailColors.accent}, transparent)`,
    borderRadius: trailRadii.pill,
    height: "2px"
  },
  primaryButton: {
    background: trailColors.accent,
    border: `1px solid ${trailColors.accentStrong}`,
    borderRadius: trailRadii.button,
    color: "#07080A",
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 800,
    justifySelf: "start",
    padding: "8px 10px"
  },
  resultItem: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.field,
    padding: trailSpacing.lg
  },
  resultList: {
    display: "grid",
    gap: trailSpacing.md,
    listStyle: "none",
    margin: 0,
    padding: 0
  },
  resultMeta: {
    color: trailColors.muted,
    fontSize: trailTypography.control,
    margin: "6px 0 0"
  },
  resultRow: {
    display: "grid",
    gap: trailSpacing.md
  },
  resultTitle: {
    color: trailColors.text,
    fontSize: "15px",
    letterSpacing: "-0.02em",
    margin: 0
  },
  searchInput: {
    background: trailColors.surfaceElevated,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.field,
    color: trailColors.text,
    fontSize: "14px",
    outline: "none",
    padding: "12px"
  },
  searchLabel: {
    display: "grid",
    gap: trailSpacing.sm
  },
  searchLabelText: {
    color: trailColors.textMuted,
    fontSize: trailTypography.control,
    fontWeight: 750
  },
  secondaryButton: {
    background: "transparent",
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.button,
    color: trailColors.textSubtle,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 700,
    justifySelf: "start",
    padding: "8px 10px"
  },
  selectedResultItem: {
    background: "linear-gradient(90deg, rgba(139, 124, 255, 0.14), rgba(139, 124, 255, 0))",
    border: `1px solid ${trailColors.accent}`,
    borderRadius: trailRadii.field,
    padding: trailSpacing.lg
  },
  solution: {
    color: trailColors.textSubtle,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: "6px 0 0"
  },
  tag: {
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.pill,
    color: trailColors.textSubtle,
    fontSize: trailTypography.caption,
    padding: "5px 7px"
  },
  tags: {
    display: "flex",
    flexWrap: "wrap",
    gap: "6px"
  },
  title: {
    color: trailColors.text,
    fontSize: "21px",
    letterSpacing: "-0.04em",
    lineHeight: 1.08,
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
