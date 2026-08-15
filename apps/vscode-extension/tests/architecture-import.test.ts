import { describe, expect, it } from "vitest";

import { extractArchitectureRuleCandidates } from "../src/project-source/architecture-import";

describe("extractArchitectureRuleCandidates", () => {
  it("extracts only clearly structured architecture rule statements", () => {
    const candidates = extractArchitectureRuleCandidates([
      "# Architecture",
      "",
      "The backend is small.",
      "- Backend persistence MUST use PostgreSQL.",
      "- Redis MUST NOT be introduced for caching or persistence.",
      "Services may use ordinary helper functions.",
      "1. Authentication SHALL go through AuthService.",
      "2. Secrets are FORBIDDEN in source code."
    ].join("\n"));

    expect(candidates.map((candidate) => candidate.statement)).toEqual([
      "Backend persistence MUST use PostgreSQL.",
      "Redis MUST NOT be introduced for caching or persistence.",
      "Authentication SHALL go through AuthService.",
      "Secrets are FORBIDDEN in source code."
    ]);
    expect(candidates.map((candidate) => candidate.title)).toEqual([
      "Backend Persistence Use PostgreSQL",
      "Redis Be Introduced For Caching Or",
      "Authentication Go Through AuthService",
      "Secrets Are In Source Code"
    ]);
  });

  it("deduplicates repeated structured statements and ignores arbitrary prose", () => {
    const candidates = extractArchitectureRuleCandidates([
      "Use boring technology.",
      "- Backend MUST use Java 17.",
      "- Backend MUST use Java 17."
    ].join("\n"));

    expect(candidates).toEqual([
      {
        title: "Backend Use Java 17",
        statement: "Backend MUST use Java 17."
      }
    ]);
  });
});
