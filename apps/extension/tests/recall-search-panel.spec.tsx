import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { RecallSearchPanel } from "../src/sidepanel/recall";

describe("RecallSearchPanel", () => {
  const onOpenResult = async (): Promise<void> => undefined;

  it("renders query, current chip, results, evidence, and degraded state", () => {
    const markup = renderToStaticMarkup(
      <RecallSearchPanel
        currentContextTitle="Spring Security CORS"
        initialQuery="cors"
        initialSearchResult={{
          degradationMode: "LEXICAL",
          query: "cors",
          results: [
            {
              errorPhrases: ["cors"],
              evidenceCodes: ["ERROR_PHRASE"],
              finalSolution: "Allowed localhost in CorsConfiguration.",
              problemSessionId: "77000000-0000-4000-8000-000000000020",
              technologies: ["Spring Security"],
              title: "Spring CORS",
              updatedAt: "2026-07-13T08:05:00.000Z"
            }
          ],
          status: "received"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: "LEXICAL", query: null, results: [], status: "received" })}
        surface="side-panel"
      />
    );

    expect(markup).toContain("Search your engineering memory");
    expect(markup).toContain("Current context · Spring Security CORS");
    expect(markup).toContain("Allowed localhost in CorsConfiguration.");
    expect(markup).toContain("Matched exact error");
    expect(markup).toContain("Semantic matching is temporarily unavailable");
    expect(markup).toContain("View trail");
    expect(markup).not.toContain("Open full library");
    expect(markup).not.toContain("Open problem");
  });

  it("renders recent, empty, auth, and error states", () => {
    const recent = renderToStaticMarkup(
      <RecallSearchPanel
        initialSearchResult={{
          degradationMode: null,
          query: null,
          results: [],
          status: "received"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: null, query: null, results: [], status: "received" })}
        surface="full-page"
      />
    );
    expect(recent).toContain("Recent solved problems");
    expect(recent).toContain("Search solved problems you already saved.");

    const empty = renderToStaticMarkup(
      <RecallSearchPanel
        initialQuery="missing"
        initialSearchResult={{
          degradationMode: null,
          query: "missing",
          results: [],
          status: "received"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: null, query: null, results: [], status: "received" })}
        surface="side-panel"
      />
    );
    expect(empty).toContain("No solved problem matches");
    expect(empty).toContain("Clear search");

    const auth = renderToStaticMarkup(
      <RecallSearchPanel
        initialSearchResult={{
          degradationMode: null,
          query: null,
          results: [],
          status: "authentication_required"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: null, query: null, results: [], status: "received" })}
        surface="side-panel"
      />
    );
    expect(auth).toContain("Reconnect Trail");

    const unavailable = renderToStaticMarkup(
      <RecallSearchPanel
        initialSearchResult={{
          degradationMode: null,
          query: "cors",
          results: [],
          status: "unavailable"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: null, query: null, results: [], status: "received" })}
        surface="side-panel"
      />
    );
    expect(unavailable).toContain("Search is temporarily unavailable");
  });

  it("renders recent solved results without requiring match evidence", () => {
    const markup = renderToStaticMarkup(
      <RecallSearchPanel
        initialSearchResult={{
          degradationMode: null,
          query: null,
          results: [
            {
              errorPhrases: [],
              evidenceCodes: [],
              finalSolution: "Fixed by correcting the API request validation.",
              problemSessionId: "77000000-0000-4000-8000-000000000021",
              technologies: [],
              title: "Recall empty search 400",
              updatedAt: "2026-07-13T08:05:00.000Z"
            }
          ],
          status: "received"
        }}
        onOpenResult={onOpenResult}
        onSearch={() => Promise.resolve({ degradationMode: null, query: null, results: [], status: "received" })}
        surface="full-page"
      />
    );

    expect(markup).toContain("Recall empty search 400");
    expect(markup).toContain("Fixed by correcting the API request validation.");
    expect(markup).toContain("Solved recently");
    expect(markup).toContain("View trail");
  });
});
