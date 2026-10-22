import { describe, expect, it } from "vitest";

import { applyEvidenceBudget, deduplicateEvidence, GroundedRagContextBuilder, rankEvidence } from "../src/ai/rag-context-builder";

import type { CodeContextPackage } from "../src/code-context/models";
import type { ProjectMemorySearcher } from "../src/code-context/code-context-package-builder";
import type { AiEvidence } from "../src/ai/models";
import type { ProjectMemorySearchResult } from "../src/memory/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("GroundedRagContextBuilder", () => {
  it("retrieves relevant memory from the selected workspace only", async () => {
    const memory = new FakeMemorySearcher([memoryResult("RULE-AUTH", "RULE", 80)]);
    const builder = new GroundedRagContextBuilder(memory);

    const context = await builder.build("Does this follow architecture?", codePackage());

    expect(memory.workspaceFolders).toEqual([workspace()]);
    expect(context.evidence.map((evidence) => evidence.id)).toContain("RULE-AUTH");
    expect(context.evidence.map((evidence) => evidence.id)).toContain("CODE-SELECTED");
  });

  it("includes relevant ADR, memory, and Git evidence", async () => {
    const builder = new GroundedRagContextBuilder(new FakeMemorySearcher([
      memoryResult("ADR-001", "DECISION", 70),
      memoryResult("RULE-001", "RULE", 90)
    ]));

    const context = await builder.build("Explain", codePackage());

    expect(context.evidence.map((evidence) => evidence.sourceType)).toEqual(
      expect.arrayContaining(["CODE", "RULE", "DECISION", "GIT_FILE", "GIT_COMMIT"])
    );
  });

  it("deduplicates evidence by ID and keeps the most relevant item", () => {
    const result = deduplicateEvidence([
      evidence({ id: "RULE-1", relevance: 10, content: "low" }),
      evidence({ id: "RULE-1", relevance: 20, content: "high" })
    ]);

    expect(result).toEqual([evidence({ id: "RULE-1", relevance: 20, content: "high" })]);
  });

  it("ranks deterministically by relevance, source authority, and ID", () => {
    const result = rankEvidence([
      evidence({ id: "z", sourceType: "GIT_COMMIT", relevance: 10 }),
      evidence({ id: "a", sourceType: "RULE", relevance: 10 }),
      evidence({ id: "b", sourceType: "CODE", relevance: 100 })
    ]);

    expect(result.map((item) => item.id)).toEqual(["b", "a", "z"]);
  });

  it("enforces context budget deterministically", () => {
    const result = applyEvidenceBudget(
      [
        evidence({ id: "a", content: "12345" }),
        evidence({ id: "b", content: "67890" })
      ],
      2,
      7
    );

    expect(result.map((item) => [item.id, item.content])).toEqual([
      ["a", "12345"],
      ["b", "67"]
    ]);
  });

  it("excludes evidence beyond max item count", async () => {
    const builder = new GroundedRagContextBuilder(new FakeMemorySearcher([
      memoryResult("RULE-1", "RULE", 90),
      memoryResult("RULE-2", "RULE", 80),
      memoryResult("RULE-3", "RULE", 70)
    ]), { maxEvidenceItems: 2 });

    const context = await builder.build("Explain", codePackage());

    expect(context.evidence).toHaveLength(2);
    expect(context.droppedEvidenceCount).toBeGreaterThan(0);
  });
});

class FakeMemorySearcher implements ProjectMemorySearcher {
  public workspaceFolders: readonly WorkspaceFolderInfo[] = [];
  private readonly results: readonly ProjectMemorySearchResult[];

  public constructor(results: readonly ProjectMemorySearchResult[]) {
    this.results = results;
  }

  public async search(_query: string, workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<readonly ProjectMemorySearchResult[]> {
    await Promise.resolve();
    this.workspaceFolders = workspaceFolders;
    return this.results;
  }
}

function codePackage(): CodeContextPackage {
  return {
    codeContext: {
      workspaceFolder: workspace(),
      filePath: "/workspace/src/AuthController.ts",
      relativePath: "src/AuthController.ts",
      fileName: "AuthController.ts",
      language: "typescript",
      symbolName: "login",
      symbolKind: "Function",
      startLine: 1,
      endLine: 3,
      selectedText: "authService.login(request);",
      selectedTextPreview: "authService.login(request);",
      isSelectionPreviewTruncated: false,
      surroundingCode: "function login() { authService.login(request); }",
      surroundingStartLine: 1,
      surroundingEndLine: 3,
      relatedImports: ["import { AuthService } from './AuthService';"]
    },
    projectEvidence: [],
    gitEvidence: {
      repository: undefined,
      fileStatus: {
        path: "src/AuthController.ts",
        previousPath: undefined,
        staged: false,
        unstaged: true,
        untracked: false,
        deleted: false,
        renamed: false
      },
      relevantCommits: [
        {
          sha: "abcdef",
          shortSha: "abcdef",
          subject: "Add auth flow",
          author: "Aayush",
          date: "2026-08-12T00:00:00.000Z"
        }
      ],
      unavailableReason: undefined
    },
    memoryResults: [memoryResult("PROJECT", "PROJECT", 10)],
    createdAt: "2026-08-12T00:00:00.000Z"
  };
}

function memoryResult(
  id: string,
  sourceType: ProjectMemorySearchResult["document"]["sourceType"],
  score: number
): ProjectMemorySearchResult {
  return {
    document: {
      id,
      sourceType,
      title: id,
      path: `/workspace/.waymark/${id}.md`,
      content: `${id} content`,
      metadata: {},
      updatedAt: "2026-08-12T00:00:00.000Z",
      workspaceFolder: workspace()
    },
    score,
    matchedFields: ["content"],
    matchedTerms: ["auth"]
  };
}

function evidence(overrides: Partial<AiEvidence> = {}): AiEvidence {
  return {
    id: "item",
    sourceType: "RULE",
    title: "Rule",
    sourcePath: "/workspace/.waymark/rules/rule.md",
    content: "content",
    relevance: 1,
    workspaceFolder: workspace(),
    ...overrides
  };
}

function workspace(): WorkspaceFolderInfo {
  return {
    name: "workspace",
    fsPath: "/workspace"
  };
}
