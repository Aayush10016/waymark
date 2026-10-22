import { describe, expect, it } from "vitest";

import { DeterministicMemoryRetriever } from "../src/memory/memory-retriever";

import type { ProjectMemoryDocument, ProjectMemorySourceType } from "../src/memory/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

const workspaceFolder: WorkspaceFolderInfo = {
  name: "backend",
  fsPath: "/backend"
};

describe("DeterministicMemoryRetriever", () => {
  it("returns empty results for empty query and no-result query", () => {
    const retriever = new DeterministicMemoryRetriever();
    const documents = [document({ title: "PostgreSQL Decision", content: "Use PostgreSQL" })];

    expect(retriever.search("", documents)).toEqual([]);
    expect(retriever.search("redis", documents)).toEqual([]);
  });

  it("matches title, path, content, metadata, phrase, and case-insensitive terms", () => {
    const retriever = new DeterministicMemoryRetriever();
    const results = retriever.search("POSTGRESQL", [
      document({
        sourceType: "DECISION",
        title: "ADR-001: Use PostgreSQL",
        path: "/backend/.waymark/decisions/ADR-001-use-postgresql.md",
        content: "Database decision",
        metadata: { database: "PostgreSQL" }
      })
    ]);

    expect(results).toHaveLength(1);
    expect(results[0]).toMatchObject({
      matchedFields: ["metadata", "path", "title"],
      matchedTerms: ["postgresql"]
    });
  });

  it("ranks exact title matches above content-only matches", () => {
    const retriever = new DeterministicMemoryRetriever();
    const results = retriever.search("PostgreSQL", [
      document({ id: "content", title: "Database Policy", content: "PostgreSQL is used here." }),
      document({ id: "title", title: "PostgreSQL", content: "" })
    ]);

    expect(results.map((result) => result.document.id)).toEqual(["title", "content"]);
  });

  it("ranks multiple-term matches above weaker matches deterministically", () => {
    const retriever = new DeterministicMemoryRetriever();
    const results = retriever.search("postgresql datasource", [
      document({ id: "one", title: "PostgreSQL", content: "" }),
      document({ id: "two", title: "PostgreSQL datasource", content: "" })
    ]);

    expect(results.map((result) => result.document.id)).toEqual(["two", "one"]);
    expect(retriever.search("postgresql datasource", results.map((result) => result.document))).toEqual(results);
  });

  it("uses explicit project source authority as a tie-breaker over Git evidence", () => {
    const retriever = new DeterministicMemoryRetriever();
    const results = retriever.search("database", [
      document({ id: "git", sourceType: "GIT_COMMIT", title: "Database", content: "" }),
      document({ id: "rule", sourceType: "RULE", title: "Database", content: "" })
    ]);

    expect(results.map((result) => result.document.id)).toEqual(["rule", "git"]);
  });

  it("keeps multi-root workspace results distinguishable", () => {
    const retriever = new DeterministicMemoryRetriever();
    const frontend: WorkspaceFolderInfo = { name: "frontend", fsPath: "/frontend" };
    const backend: WorkspaceFolderInfo = { name: "backend", fsPath: "/backend" };

    const results = retriever.search("architecture", [
      document({ id: "frontend-rule", workspaceFolder: frontend, title: "Architecture Rule" }),
      document({ id: "backend-rule", workspaceFolder: backend, title: "Architecture Rule" })
    ]);

    expect(results.map((result) => result.document.workspaceFolder.name)).toEqual(["backend", "frontend"]);
  });
});

function document(overrides: Partial<ProjectMemoryDocument>): ProjectMemoryDocument {
  return {
    id: "doc",
    sourceType: "RULE" satisfies ProjectMemorySourceType,
    title: "Document",
    path: "/backend/.waymark/rules/document.md",
    content: "",
    metadata: {},
    updatedAt: "2026-08-11T00:00:00.000Z",
    workspaceFolder,
    ...overrides
  };
}
