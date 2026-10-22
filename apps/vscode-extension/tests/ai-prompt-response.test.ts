import { describe, expect, it } from "vitest";

import { GroundedPromptBuilder } from "../src/ai/prompt-builder";
import { buildGroundedAiResponse } from "../src/ai/response-builder";

import type { AiEvidence, GroundedRagContext } from "../src/ai/models";
import type { GuardianCheckResult } from "../src/guardian/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("GroundedPromptBuilder", () => {
  it("includes project evidence, code context, Git evidence, and anti-fabrication instructions", () => {
    const prompt = new GroundedPromptBuilder().build(ragContext([
      evidence({ id: "RULE-AUTH", sourceType: "RULE", title: "Auth rule" }),
      evidence({ id: "CODE-SELECTED", sourceType: "CODE", title: "AuthController.ts" }),
      evidence({ id: "GIT-COMMIT-abc", sourceType: "GIT_COMMIT", title: "abc Add auth" })
    ]), guardianResult());

    expect(prompt.system).toContain("Project evidence supplied by Waymark is authoritative");
    expect(prompt.system).toContain("Never claim that a rule exists unless it is supplied.");
    expect(prompt.system).toContain("Do not fabricate Git history.");
    expect(prompt.user).toContain("RULE-AUTH");
    expect(prompt.user).toContain("CODE-SELECTED");
    expect(prompt.user).toContain("GIT-COMMIT-abc");
    expect(prompt.user).toContain("I don't have enough project evidence to determine that.");
  });

  it("preserves Guardian findings without replacing deterministic results", () => {
    const prompt = new GroundedPromptBuilder().build(ragContext([evidence()]), guardianResult({
      findings: [{
        id: "finding",
        severity: "WARNING",
        title: "Potential project-rule conflict",
        message: "Direct JWT validation bypasses AuthService.",
        ruleId: "auth",
        ruleTitle: "Authentication must go through AuthService",
        rulePath: "/workspace/.waymark/rules/auth.md",
        matchedTerms: ["jwt"],
        evidence: [],
        source: "SELECTED_CODE",
        workspaceFolder: workspace()
      }]
    }));

    expect(prompt.user).toContain("Potential project-rule conflict");
    expect(prompt.user).toContain("Direct JWT validation bypasses AuthService.");
  });
});

describe("buildGroundedAiResponse", () => {
  it("maps evidence ID citations back to structured evidence references", () => {
    const prompt = new GroundedPromptBuilder().build(ragContext([
      evidence({ id: "RULE-AUTH", sourceType: "RULE", title: "Auth rule" }),
      evidence({ id: "ADR-001", sourceType: "DECISION", title: "Use AuthService" })
    ]), guardianResult());

    const response = buildGroundedAiResponse({
      text: "Use AuthService according to [RULE-AUTH].",
      provider: "nvidia",
      model: "test-model"
    }, prompt);

    expect(response.evidenceReferences.map((reference) => reference.evidenceId)).toEqual(["RULE-AUTH"]);
    expect(response.grounded).toBe(true);
  });

  it("warns when provider response does not cite supplied evidence IDs", () => {
    const prompt = new GroundedPromptBuilder().build(ragContext([evidence({ id: "RULE-AUTH" })]), guardianResult());
    const response = buildGroundedAiResponse({
      text: "This looks fine.",
      provider: "nvidia",
      model: "test-model"
    }, prompt);

    expect(response.grounded).toBe(false);
    expect(response.warnings).toHaveLength(1);
  });
});

function ragContext(evidenceItems: readonly AiEvidence[]): GroundedRagContext {
  return {
    question: "Does this follow project architecture?",
    evidence: evidenceItems,
    droppedEvidenceCount: 0
  };
}

function guardianResult(overrides: Partial<GuardianCheckResult> = {}): GuardianCheckResult {
  return {
    findings: [],
    checkedRules: 1,
    checkedDecisions: 1,
    checkedFiles: 1,
    ...overrides
  };
}

function evidence(overrides: Partial<AiEvidence> = {}): AiEvidence {
  return {
    id: "CODE-SELECTED",
    sourceType: "CODE",
    title: "Selected code",
    sourcePath: "/workspace/src/AuthController.ts",
    content: "authService.login(request);",
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
