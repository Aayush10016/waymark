import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { SimilarProblemRecommendationPayload } from "@trail/extension-messages";
import {
  GhostReplayPanel,
  ghostReplayStyles,
  initialGhostReplayProgress,
  markGhostReplayResourceOpened,
  selectNextGhostReplayResource
} from "../src/sidepanel/recall";

describe("GhostReplayPanel", () => {
  it("renders resource progression without opening tabs automatically", () => {
    let opened = false;
    const markup = renderToStaticMarkup(
      <GhostReplayPanel
        onBackToSolution={() => undefined}
        onFullTrail={() => undefined}
        onOpenResource={() => {
          opened = true;
          return Promise.resolve(true);
        }}
        recommendation={recommendation()}
      />
    );

    expect(markup).toContain("Ghost Replay");
    expect(markup).toContain("Current path");
    expect(markup).toContain("Previous path");
    expect(markup).toContain("Allowed localhost in CorsConfiguration.");
    expect(markup).toContain("Replay one resource at a time");
    expect(markup).toContain("Open decisive resource");
    expect(markup).toContain("Decisive resource");
    expect(markup).toContain("Already visited in this session");
    expect(markup).toContain("Can&#x27;t open this URL safely");
    expect(markup).toContain('data-layout="left-right narrow-aware"');
    expect(markup).toContain('data-motion="prefers-reduced-motion-safe"');
    expect(markup).not.toContain("Opened now");
    expect(opened).toBe(false);
  });

  it("defines choreography, reduced-motion, narrow-panel, and focus-visible states", () => {
    expect(ghostReplayStyles).toContain("@keyframes trailGhostReplayEnter");
    expect(ghostReplayStyles).toContain("@keyframes trailGhostPathAlign");
    expect(ghostReplayStyles).toContain("@media (prefers-reduced-motion: reduce)");
    expect(ghostReplayStyles).toContain("@media (max-width: 420px)");
    expect(ghostReplayStyles).toContain(":focus-visible");
  });

  it("selects decisive resources first, then progresses to the next unopened resource", () => {
    const progress = initialGhostReplayProgress(recommendation().resources ?? []);

    expect(progress.activeResourceId).toBe("77000000-0000-4000-8000-000000000004");

    const afterOpen = markGhostReplayResourceOpened(progress, "77000000-0000-4000-8000-000000000004");
    expect(afterOpen.resources[0]?.openedNow).toBe(true);
    expect(afterOpen.activeResourceId).toBeNull();

    expect(selectNextGhostReplayResource(progress).activeResourceId).toBeNull();
  });
});

function recommendation(): SimilarProblemRecommendationPayload {
  return {
    algorithmVersion: "hybrid-similarity-v1",
    durationSeconds: 8040,
    evidence: [{ evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000003" }],
    finalSolution: "Allowed localhost in CorsConfiguration.",
    recommendationId: "77000000-0000-4000-8000-000000000002",
    recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
    resources: [
      {
        alreadyVisited: true,
        decisive: true,
        hostname: "docs.spring.io",
        id: "77000000-0000-4000-8000-000000000004",
        kind: "DOCUMENTATION",
        occurredAt: "2026-01-11T08:05:00.000Z",
        openedNow: false,
        title: "Spring CORS docs",
        url: "https://docs.spring.io/cors"
      },
      {
        alreadyVisited: false,
        decisive: false,
        id: "77000000-0000-4000-8000-000000000005",
        kind: "SEARCH_QUERY",
        occurredAt: "2026-01-11T08:06:00.000Z",
        openedNow: false,
        queryText: "spring security cors",
        url: null
      }
    ],
    solvedAt: "2026-01-11T08:00:00.000Z",
    strength: "STRONG",
    title: "Spring Security CORS",
    whyThis: [
      {
        evidenceCode: "ERROR_PHRASE",
        message: "Both problems mention CORS.",
        problemSessionId: "77000000-0000-4000-8000-000000000003"
      }
    ]
  };
}
