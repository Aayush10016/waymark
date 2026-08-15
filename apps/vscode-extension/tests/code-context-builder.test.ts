import { describe, expect, it } from "vitest";

import { CodeContextBuilder, extractImports } from "../src/code-context/code-context-builder";

import type { SourceSelection } from "../src/code-context/models";

describe("CodeContextBuilder", () => {
  it("captures selected text, file metadata, language, line range, imports, and symbol information", () => {
    const builder = new CodeContextBuilder({ surroundingLineRadius: 1, selectionPreviewLimit: 500 });
    const context = builder.build(
      selection({
        selectedText: "authenticate(token);",
        startLine: 5,
        endLine: 5,
        symbols: [
          {
            name: "AuthService",
            kind: "Class",
            startLine: 3,
            endLine: 8,
            children: [
              {
                name: "authenticate",
                kind: "Method",
                startLine: 5,
                endLine: 5
              }
            ]
          }
        ]
      })
    );

    expect(context).toMatchObject({
      relativePath: "src/AuthService.java",
      fileName: "AuthService.java",
      language: "java",
      symbolName: "authenticate",
      symbolKind: "Method",
      startLine: 5,
      endLine: 5,
      selectedText: "authenticate(token);",
      relatedImports: ["import java.util.List;"]
    });
    expect(context.surroundingStartLine).toBe(4);
    expect(context.surroundingEndLine).toBe(6);
  });

  it("falls back safely when symbol information is unavailable", () => {
    const builder = new CodeContextBuilder();

    expect(builder.build(selection({ symbols: [] }))).toMatchObject({
      symbolName: undefined,
      symbolKind: undefined
    });
  });

  it("bounds surrounding code and large selection preview", () => {
    const builder = new CodeContextBuilder({ surroundingLineRadius: 2, selectionPreviewLimit: 10 });
    const context = builder.build(
      selection({
        selectedText: "x".repeat(20),
        startLine: 10,
        endLine: 10,
        fullText: Array.from({ length: 30 }, (_, index) => `line ${index + 1}`).join("\n")
      })
    );

    expect(context.surroundingStartLine).toBe(8);
    expect(context.surroundingEndLine).toBe(12);
    expect(context.selectedTextPreview).toBe("x".repeat(10));
    expect(context.isSelectionPreviewTruncated).toBe(true);
  });

  it("extracts common import forms without resolving dependencies", () => {
    expect(
      extractImports(
        [
          "import java.util.List;",
          "from pathlib import Path",
          "using System;",
          "#include <vector>",
          "const fs = require('node:fs');",
          "const value = 1;"
        ].join("\n")
      )
    ).toEqual([
      "import java.util.List;",
      "from pathlib import Path",
      "using System;",
      "#include <vector>",
      "const fs = require('node:fs');"
    ]);
  });
});

function selection(overrides: Partial<SourceSelection>): SourceSelection {
  const fullText =
    overrides.fullText ??
    [
      "package auth;",
      "import java.util.List;",
      "",
      "class AuthService {",
      "  authenticate(token);",
      "}"
    ].join("\n");

  return {
    workspaceFolder: {
      name: "backend",
      fsPath: "/workspace/backend"
    },
    filePath: "/workspace/backend/src/AuthService.java",
    relativePath: "src/AuthService.java",
    fileName: "AuthService.java",
    language: "java",
    fullText,
    selectedText: "authenticate(token);",
    startLine: 5,
    endLine: 5,
    selectionStartOffset: 0,
    selectionEndOffset: 20,
    symbols: [],
    ...overrides
  };
}
