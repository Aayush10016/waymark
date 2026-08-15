import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ActiveProblemSessionPayload } from "@trail/extension-messages";
import { FinalSolutionEntryStep } from "../src/sidepanel/problem-session";
import type { FinalSolutionDraftService } from "../src/services/drafts";

describe("FinalSolutionEntryStep", () => {
  it("renders user-authored solution entry with no AI suggestion", () => {
    const markup = renderToStaticMarkup(
      <FinalSolutionEntryStep draftService={new StaticDraftService()} onBack={() => undefined} session={activeSession()} />
    );

    expect(markup).toContain("What finally solved it?");
    expect(markup).toContain("textarea");
    expect(markup).toContain("Save solution");
    expect(markup).toContain("Review context");
    expect(markup).toContain("One or two sentences is enough");
    expect(markup).not.toContain("AI suggestion");
    expect(markup).not.toContain("Suggested solution");
    expect(markup).not.toContain("Generated answer");
  });

  it("renders context review affordance without exposing raw URLs", () => {
    const markup = renderToStaticMarkup(
      <FinalSolutionEntryStep
        artifacts={[
          {
            artifactType: "DOCUMENTATION",
            hostname: "docs.spring.io",
            id: "artifact-1",
            occurredAt: "2026-07-16T08:02:00.000Z",
            title: "Spring Security CORS"
          }
        ]}
        draftService={new StaticDraftService()}
        onBack={() => undefined}
        session={activeSession()}
      />
    );

    expect(markup).toContain("Review context");
    expect(markup).not.toContain("https://");
    expect(markup).not.toContain("utm_");
  });
});

class StaticDraftService implements FinalSolutionDraftService {
  acknowledgeDeleted(): Promise<void> {
    return Promise.resolve();
  }

  read(): Promise<null> {
    return Promise.resolve(null);
  }

  save() {
    return Promise.resolve({
      draft: {
        acknowledgedAt: null,
        createdAt: "2026-07-16T08:00:00.000Z",
        problemSessionId: activeSession().id,
        schemaVersion: 1 as const,
        text: "",
        updatedAt: "2026-07-16T08:00:00.000Z"
      },
      evictedDrafts: []
    });
  }
}

function activeSession(): ActiveProblemSessionPayload {
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
