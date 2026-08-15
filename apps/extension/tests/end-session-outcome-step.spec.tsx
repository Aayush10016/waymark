import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ActiveProblemSessionPayload, ProblemSessionResponsePayload } from "@trail/extension-messages";
import type {
  EndSessionProblemSessionClient,
  EndSessionProblemSessionClientResult
} from "../src/sidepanel/problem-session";
import { EndSessionOutcomeStep } from "../src/sidepanel/problem-session";
import { TrailSidePanelApp } from "../src/sidepanel/TrailSidePanelApp";

describe("EndSessionOutcomeStep", () => {
  it("renders the outcome question with no selected result and primary focus on Solved", () => {
    const markup = renderToStaticMarkup(
      <EndSessionOutcomeStep client={new StaticEndSessionClient(problemSessionState())} initialProblemSessionState={problemSessionState()} />
    );

    expect(markup).toContain("Did you solve it?");
    expect(markup).toContain("Technical problem");
    expect(markup).toContain("Solved");
    expect(markup).toContain("Not yet");
    expect(markup).toContain("Not a problem");
    expect(markup).toContain("Ask me later");
    expect(markup).toContain("autofocus=\"\"");
    expect(markup).not.toContain("textarea");
    expect(markup).not.toContain("Save solution");
  });

  it("renders no active problem empty state without raw captured activity", () => {
    const markup = renderToStaticMarkup(
      <EndSessionOutcomeStep
        client={new StaticEndSessionClient(problemSessionState(null))}
        initialProblemSessionState={problemSessionState(null)}
      />
    );

    expect(markup).toContain("No active problem");
    expect(markup).not.toContain("https://");
    expect(markup).not.toContain("CandidateDetectionDecision");
  });

  it("uses zero transition duration when reduced motion is requested", () => {
    const markup = renderToStaticMarkup(
      <EndSessionOutcomeStep
        client={new StaticEndSessionClient(problemSessionState())}
        initialProblemSessionState={problemSessionState()}
        prefersReducedMotion
      />
    );

    expect(markup).toContain("transition-duration:0ms");
  });

  it("renders side panel loading and unavailable states safely", () => {
    expect(renderToStaticMarkup(<TrailSidePanelApp initialProblemSessionState={null} />)).toContain("Trail unavailable");
  });
});

class StaticEndSessionClient implements EndSessionProblemSessionClient {
  constructor(private readonly state: ProblemSessionResponsePayload) {}

  dismiss(): Promise<EndSessionProblemSessionClientResult> {
    return Promise.resolve({ payload: problemSessionState(null), status: "received" });
  }

  finish(): Promise<EndSessionProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  getState(): Promise<EndSessionProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  saveSolution(): Promise<EndSessionProblemSessionClientResult> {
    return Promise.resolve({
      payload: problemSessionState(null),
      status: "received"
    });
  }
}

function problemSessionState(
  activeSession: ProblemSessionResponsePayload["state"]["activeSession"] = activeProblemSession()
): ProblemSessionResponsePayload {
  return {
    commandStatus: "received",
    state: {
      activeSession,
      dismissedSession: null
    }
  };
}

function activeProblemSession(): ActiveProblemSessionPayload {
  return {
    detectionReasons: ["manual_session"],
    endedAt: "2026-07-16T08:05:00.000Z",
    etag: "\"1\"",
    id: "10000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "REVIEWABLE",
    title: "Technical problem",
    updatedAt: "2026-07-16T08:05:00.000Z",
    version: 1
  };
}
