import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ProblemSessionResponsePayload, RecallStatePayload } from "@trail/extension-messages";
import { TrailSidePanelApp } from "../src/sidepanel/TrailSidePanelApp";

describe("Recall side panel routing", () => {
  it("routes no-match recall state to the search boundary", () => {
    const markup = renderToStaticMarkup(
      <TrailSidePanelApp initialProblemSessionState={problemSessionState()} initialRecallState={searchRecallState()} />
    );

    expect(markup).toContain("Trail Recall");
    expect(markup).toContain("Recent solved problems");
    expect(markup).toContain("Search solved problems you already saved.");
    expect(markup).toContain("Search problems, errors, technologies, or solutions");
  });

  it("routes matched recall state to the similar problem boundary without rendering future card details", () => {
    const markup = renderToStaticMarkup(
      <TrailSidePanelApp initialProblemSessionState={problemSessionState()} initialRecallState={similarRecallState()} />
    );

    expect(markup).toContain("Similar Problem Found");
    expect(markup).toContain("Allowed localhost in CorsConfiguration.");
    expect(markup).not.toContain("score");
    expect(markup).not.toContain("time saved");
  });

  it("routes explicit Finish intent to end-session even when stale Recall state exists", () => {
    const markup = renderToStaticMarkup(
      <TrailSidePanelApp
        initialProblemSessionState={problemSessionState(reviewableSession())}
        initialRecallState={searchRecallState()}
        initialRouteIntent="end_session"
      />
    );

    expect(markup).toContain("Did you solve it?");
    expect(markup).toContain("Solved");
    expect(markup).not.toContain("Trail Recall");
  });

  it("shows a finishing state for End Session intent while the finish command is still settling", () => {
    const markup = renderToStaticMarkup(
      <TrailSidePanelApp
        initialProblemSessionState={problemSessionState(candidateSession())}
        initialRecallState={searchRecallState()}
        initialRouteIntent="end_session"
      />
    );

    expect(markup).toContain("Finishing problem");
    expect(markup).not.toContain("Trail Recall");
  });
});

function searchRecallState(): RecallStatePayload {
  return {
    commandStatus: "no_match",
    invokedAt: "2026-07-13T08:00:00.000Z",
    route: {
      reason: "NO_MATCH",
      route: "search"
    },
    source: "popup"
  };
}

function similarRecallState(): RecallStatePayload {
  return {
    commandStatus: "matched",
    invokedAt: "2026-07-13T08:00:00.000Z",
    route: {
      currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
      recommendation: similarRecommendation(),
      recommendationId: "77000000-0000-4000-8000-000000000002",
      recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
      route: "similar_problem"
    },
    source: "popup"
  };
}

function similarRecommendation(): Extract<RecallStatePayload["route"], { route: "similar_problem" }>["recommendation"] {
  return {
    algorithmVersion: "hybrid-similarity-v1",
    durationSeconds: 8040,
    evidence: [
      { evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "TECHNOLOGY", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "LEXICAL_TEXT", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "VECTOR_DISTANCE", problemSessionId: "77000000-0000-4000-8000-000000000003" }
    ],
    finalSolution: "Allowed localhost in CorsConfiguration.",
    recommendationId: "77000000-0000-4000-8000-000000000002",
    recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
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

function problemSessionState(
  activeSession: ProblemSessionResponsePayload["state"]["activeSession"] = null
): ProblemSessionResponsePayload {
  return {
    commandStatus: "received",
    state: {
      activeSession,
      dismissedSession: null
    }
  };
}

function candidateSession(): NonNullable<ProblemSessionResponsePayload["state"]["activeSession"]> {
  return {
    detectionReasons: ["manual_session"],
    endedAt: null,
    etag: "\"0\"",
    id: "10000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "CANDIDATE",
    title: "Technical problem",
    updatedAt: "2026-07-16T08:00:00.000Z",
    version: 0
  };
}

function reviewableSession(): NonNullable<ProblemSessionResponsePayload["state"]["activeSession"]> {
  return {
    ...candidateSession(),
    endedAt: "2026-07-16T08:05:00.000Z",
    etag: "\"1\"",
    status: "REVIEWABLE",
    updatedAt: "2026-07-16T08:05:00.000Z",
    version: 1
  };
}
