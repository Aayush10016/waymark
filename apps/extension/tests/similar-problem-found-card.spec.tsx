import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { SimilarProblemRecommendationPayload } from "@trail/extension-messages";
import { SimilarProblemFoundCard, ageLabel, durationLabel } from "../src/sidepanel/recall";

describe("SimilarProblemFoundCard", () => {
  it("renders exact solution, age, duration, evidence, and why-this without score or time-saved claims", () => {
    const markup = renderToStaticMarkup(
      <SimilarProblemFoundCard
        now={new Date("2026-07-13T08:00:00.000Z")}
        onReplay={() => undefined}
        recommendation={recommendation()}
      />
    );

    expect(markup).toContain("Allowed localhost in CorsConfiguration and registered the JWT filter before UsernamePasswordAuthenticationFilter.");
    expect(markup).toContain("183 days ago");
    expect(markup).toContain("2h 14m");
    expect(markup).toContain("Same error phrase");
    expect(markup).toContain("Shared technology");
    expect(markup).toContain("Similar problem text");
    expect(markup).toContain("Both problems mention CORS.");
    expect(markup).not.toContain("Similar debugging context");
    expect(markup.toLowerCase()).not.toContain("score");
    expect(markup.toLowerCase()).not.toContain("time saved");
  });

  it("handles missing solved-at and duration states", () => {
    expect(ageLabel(null, new Date("2026-07-13T08:00:00.000Z"))).toBe("Previously solved");
    expect(durationLabel(null)).toBe("Duration unknown");
  });
});

function recommendation(): SimilarProblemRecommendationPayload {
  return {
    algorithmVersion: "hybrid-similarity-v1",
    durationSeconds: 8040,
    evidence: [
      { evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "TECHNOLOGY", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "LEXICAL_TEXT", problemSessionId: "77000000-0000-4000-8000-000000000003" },
      { evidenceCode: "VECTOR_DISTANCE", problemSessionId: "77000000-0000-4000-8000-000000000003" }
    ],
    finalSolution: "Allowed localhost in CorsConfiguration and registered the JWT filter before UsernamePasswordAuthenticationFilter.",
    recommendationId: "77000000-0000-4000-8000-000000000002",
    recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
    solvedAt: "2026-01-11T08:00:00.000Z",
    strength: "STRONG",
    title: "Spring Security CORS blocked by JWT filter order",
    whyThis: [
      {
        evidenceCode: "ERROR_PHRASE",
        message: "Both problems mention CORS.",
        problemSessionId: "77000000-0000-4000-8000-000000000003"
      }
    ]
  };
}
