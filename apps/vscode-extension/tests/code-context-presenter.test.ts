import { describe, expect, it } from "vitest";

import { formatCodeContextPackage } from "../src/code-context/code-context-presenter";

import type { CodeContextPackage } from "../src/code-context/models";

describe("formatCodeContextPackage", () => {
  it("renders selected code, evidence categories, and the local-only state", () => {
    const output = formatCodeContextPackage(contextPackage());

    expect(output).toContain("TRAIL CODE CONTEXT");
    expect(output).toContain("Local code context prepared. No AI was used.");
    expect(output).toContain("CODE");
    expect(output).toContain("PROJECT EVIDENCE");
    expect(output).toContain("GIT EVIDENCE");
    expect(output).toContain("MEMORY EVIDENCE");
    expect(output).toContain("authenticate(token);");
  });
});

function contextPackage(): CodeContextPackage {
  const workspaceFolder = { name: "backend", fsPath: "/workspace/backend" };
  return {
    codeContext: {
      workspaceFolder,
      filePath: "/workspace/backend/src/AuthService.java",
      relativePath: "src/AuthService.java",
      fileName: "AuthService.java",
      language: "java",
      symbolName: "authenticate",
      symbolKind: "Method",
      startLine: 42,
      endLine: 67,
      selectedText: "authenticate(token);",
      selectedTextPreview: "authenticate(token);",
      isSelectionPreviewTruncated: false,
      surroundingCode: "authenticate(token);",
      surroundingStartLine: 32,
      surroundingEndLine: 77,
      relatedImports: ["import java.util.List;"]
    },
    projectEvidence: [],
    gitEvidence: {
      repository: {
        rootPath: "/workspace/backend",
        branch: "feature/auth",
        headCommit: "a31f92c",
        isDetachedHead: false
      },
      fileStatus: undefined,
      relevantCommits: [],
      unavailableReason: undefined
    },
    memoryResults: [],
    createdAt: "2026-08-11T00:00:00.000Z"
  };
}
