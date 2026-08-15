import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ProblemSummary } from "../src/sidepanel/problem-session";

describe("ProblemSummary", () => {
  it("renders the exact user solution before generated fields", () => {
    const markup = renderToStaticMarkup(
      <ProblemSummary
        artifacts={[
          {
            artifactType: "DOCUMENTATION",
            hostname: "docs.spring.io",
            id: "artifact-1",
            occurredAt: "2026-07-16T08:02:00.000Z",
            title: "Spring Security CORS"
          }
        ]}
        session={session()}
        solution="Allowed localhost:3000 in CorsConfiguration."
      />
    );

    expect(markup.indexOf("Allowed localhost:3000 in CorsConfiguration.")).toBeLessThan(
      markup.indexOf("Matching details")
    );
    expect(markup).toContain("Your solution");
    expect(markup).toContain("User-authored");
    expect(markup).toContain("Semantic matching is not required for this saved problem.");
    expect(markup).not.toContain("Generated details loading");
    expect(markup).toContain("Edit solution");
    expect(markup).toContain("Spring Security CORS");
    expect(markup).not.toContain("AI suggestion");
  });

  it("renders offline and empty-resource states without hiding the solution", () => {
    const markup = renderToStaticMarkup(
      <ProblemSummary
        artifacts={[]}
        initialStatus="offline"
        session={session()}
        solution="JWT filter was registered before UsernamePasswordAuthenticationFilter."
      />
    );

    expect(markup).toContain("JWT filter was registered before UsernamePasswordAuthenticationFilter.");
    expect(markup).toContain("Saved on this device. Trail will sync when online.");
    expect(markup).not.toContain("No resources");
  });
});

function session() {
  return {
    detectionReasons: ["manual_session"],
    endedAt: "2026-07-16T08:14:00.000Z",
    etag: "\"2\"",
    id: "10000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "SOLVED",
    title: "Spring Security CORS not working",
    updatedAt: "2026-07-16T08:14:00.000Z",
    version: 2
  } as const;
}
