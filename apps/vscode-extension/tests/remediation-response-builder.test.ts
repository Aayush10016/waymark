import { describe, expect, it } from "vitest";

import { buildRemediationResponse } from "../src/ai/remediation-response-builder";
import { AiProviderError } from "../src/ai/provider";

import type { GroundedPrompt } from "../src/ai/models";

describe("buildRemediationResponse", () => {
  it("parses a structured remediation proposal and maps evidence references", () => {
    const response = buildRemediationResponse({
      provider: "nvidia",
      model: "test-model",
      text: JSON.stringify({
        summary: "Remove Redis import.",
        rationale: "Removing RedisTemplate satisfies the no-Redis rule.",
        confidence: "high",
        evidenceReferences: ["CODE-SELECTED", "RULE-NO-REDIS"],
        changes: [{
          filePath: "/workspace/backend/src/Foo.java",
          startLine: 1,
          endLine: 1,
          expectedOriginalText: "import org.springframework.data.redis.core.RedisTemplate;",
          replacementText: "",
          reason: "This import introduces RedisTemplate."
        }],
        warnings: []
      })
    }, prompt());

    expect(response.summary).toBe("Remove Redis import.");
    expect(response.confidence).toBe("high");
    expect(response.evidenceReferences.map((reference) => reference.evidenceId)).toEqual(["CODE-SELECTED", "RULE-NO-REDIS"]);
    expect(response.changes).toHaveLength(1);
    expect(response.changes[0]?.expectedOriginalText).toBe("import org.springframework.data.redis.core.RedisTemplate;");
  });

  it("rejects malformed AI response text", () => {
    expect(() => buildRemediationResponse({
      provider: "nvidia",
      model: "test-model",
      text: "Remove Redis."
    }, prompt())).toThrow(AiProviderError);
  });

  it("rejects proposed edits without expected original text", () => {
    expect(() => buildRemediationResponse({
      provider: "nvidia",
      model: "test-model",
      text: JSON.stringify({
        summary: "Remove Redis import.",
        rationale: "It resolves the rule.",
        confidence: "high",
        evidenceReferences: ["CODE-SELECTED"],
        changes: [{
          filePath: "/workspace/backend/src/Foo.java",
          startLine: 1,
          endLine: 1,
          replacementText: "",
          reason: "Remove import."
        }],
        warnings: []
      })
    }, prompt())).toThrow(AiProviderError);
  });

  it("allows low-confidence insufficient-evidence responses with no changes", () => {
    const response = buildRemediationResponse({
      provider: "nvidia",
      model: "test-model",
      text: JSON.stringify({
        summary: "No safe fix proposed.",
        rationale: "Insufficient project evidence.",
        confidence: "low",
        evidenceReferences: ["CODE-SELECTED"],
        changes: [],
        warnings: ["Insufficient evidence to safely propose a fix."]
      })
    }, prompt());

    expect(response.changes).toEqual([]);
    expect(response.warnings).toEqual(["Insufficient evidence to safely propose a fix."]);
  });

  it("does not expose provider secrets in malformed-response errors", () => {
    const secret = "super-secret-nvidia-key";
    try {
      buildRemediationResponse({
        provider: "nvidia",
        model: "test-model",
        text: `{ "summary": "${secret}" `
      }, prompt());
      throw new Error("Expected parse failure.");
    } catch (error) {
      expect(error).toBeInstanceOf(AiProviderError);
      expect((error as Error).message).not.toContain(secret);
    }
  });
});

function prompt(): GroundedPrompt {
  return {
    system: "system",
    user: "user",
    evidence: [
      {
        id: "CODE-SELECTED",
        sourceType: "CODE",
        title: "Selected code",
        sourcePath: "/workspace/backend/src/Foo.java",
        content: "RedisTemplate",
        relevance: 100,
        workspaceFolder: workspace()
      },
      {
        id: "RULE-NO-REDIS",
        sourceType: "RULE",
        title: "Redis is forbidden",
        sourcePath: "/workspace/.trail/rules/no-redis.md",
        content: "Redis must not be used anywhere.",
        relevance: 90,
        workspaceFolder: workspace()
      }
    ]
  };
}

function workspace(): { readonly name: string; readonly fsPath: string } {
  return {
    name: "workspace",
    fsPath: "/workspace"
  };
}
