import { describe, expect, it } from "vitest";

import { RemediationPromptBuilder } from "../src/ai/remediation-prompt-builder";

import type { CodeContextPackage } from "../src/code-context/models";
import type { AiEvidence, GroundedRagContext } from "../src/ai/models";
import type { GuardianFinding } from "../src/guardian/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("RemediationPromptBuilder", () => {
  it("constructs a grounded remediation prompt with code, rule, finding, memory, decision, and Git evidence", () => {
    const prompt = new RemediationPromptBuilder().build(codePackage(), ragContext(), finding());

    expect(prompt.system).toContain("Project rules supplied by Waymark are authoritative.");
    expect(prompt.system).toContain("Do not invent project rules.");
    expect(prompt.system).toContain("Only propose changes necessary to resolve the supplied deterministic Guardian finding.");
    expect(prompt.user).toContain("Redis must not be used anywhere in the backend.");
    expect(prompt.user).toContain("RedisTemplate");
    expect(prompt.user).toContain("CODE-SELECTED");
    expect(prompt.user).toContain("RULE-NO-REDIS");
    expect(prompt.user).toContain("ADR-INFRA");
    expect(prompt.user).toContain("GIT-FILE-backend/src/ArcadeScoreController.java");
    expect(prompt.user).toContain("\"expectedOriginalText\"");
    expect(prompt.user).toContain("Return only valid JSON.");
  });
});

function codePackage(): CodeContextPackage {
  return {
    codeContext: {
      workspaceFolder: workspace(),
      filePath: "/workspace/backend/src/ArcadeScoreController.java",
      relativePath: "backend/src/ArcadeScoreController.java",
      fileName: "ArcadeScoreController.java",
      language: "java",
      symbolName: "ArcadeScoreController",
      symbolKind: "Class",
      startLine: 1,
      endLine: 1,
      selectedText: "import org.springframework.data.redis.core.RedisTemplate;",
      selectedTextPreview: "import org.springframework.data.redis.core.RedisTemplate;",
      isSelectionPreviewTruncated: false,
      surroundingCode: "import org.springframework.data.redis.core.RedisTemplate;\nclass ArcadeScoreController {}",
      surroundingStartLine: 1,
      surroundingEndLine: 2,
      relatedImports: ["import org.springframework.data.redis.core.RedisTemplate;"]
    },
    projectEvidence: [],
    gitEvidence: {
      repository: undefined,
      fileStatus: {
        path: "backend/src/ArcadeScoreController.java",
        previousPath: undefined,
        staged: false,
        unstaged: true,
        untracked: false,
        deleted: false,
        renamed: false
      },
      relevantCommits: [{
        sha: "abcdef",
        shortSha: "abcdef",
        subject: "Add score controller",
        author: "Aayush",
        date: "2026-08-14T00:00:00.000Z"
      }],
      unavailableReason: undefined
    },
    memoryResults: [],
    createdAt: "2026-08-14T00:00:00.000Z"
  };
}

function ragContext(): GroundedRagContext {
  return {
    question: "Suggest a remediation",
    evidence: [
      evidence({ id: "CODE-SELECTED", sourceType: "CODE", title: "Selected code", content: "RedisTemplate" }),
      evidence({
        id: "RULE-NO-REDIS",
        sourceType: "RULE",
        title: "Redis is forbidden",
        content: "Redis must not be used anywhere in the backend."
      }),
      evidence({ id: "ADR-INFRA", sourceType: "DECISION", title: "Keep infra simple", content: "Avoid unnecessary infrastructure." }),
      evidence({ id: "GIT-FILE-backend/src/ArcadeScoreController.java", sourceType: "GIT_FILE", title: "Changed file" })
    ],
    droppedEvidenceCount: 0
  };
}

function finding(): GuardianFinding {
  return {
    id: "finding",
    severity: "WARNING",
    title: "Potential project-rule conflict",
    message: "Redis is forbidden in backend code.",
    ruleId: "no-redis",
    ruleTitle: "Redis is forbidden",
    rulePath: "/workspace/.waymark/rules/no-redis.md",
    matchedTerms: ["RedisTemplate"],
    evidence: [{
      kind: "RULE",
      title: "Redis is forbidden",
      detail: "Redis must not be used anywhere in the backend.",
      path: "/workspace/.waymark/rules/no-redis.md"
    }],
    source: "AUTOMATIC_FILE_CHANGE",
    workspaceFolder: workspace(),
    codePath: "/workspace/backend/src/ArcadeScoreController.java"
  };
}

function evidence(overrides: Partial<AiEvidence> = {}): AiEvidence {
  return {
    id: "EVIDENCE",
    sourceType: "RULE",
    title: "Evidence",
    sourcePath: "/workspace/.waymark/rules/evidence.md",
    content: "Evidence content",
    relevance: 100,
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
