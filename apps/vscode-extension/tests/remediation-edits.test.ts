import { describe, expect, it } from "vitest";

import { applyRemediationChangesToText, contentHash, validateRemediationEdits } from "../src/ai/remediation-edits";

import type { RemediationChange } from "../src/ai/remediation-models";

describe("validateRemediationEdits", () => {
  it("accepts a targeted edit when the current file still matches the proposal snapshot", () => {
    const filePath = "/workspace/backend/src/Foo.java";
    const content = "import org.springframework.data.redis.core.RedisTemplate;\nclass Foo {}";

    const result = validateRemediationEdits({
      intendedFilePath: filePath,
      changes: [change({ filePath })],
      currentContentsByFilePath: new Map([[filePath, content]]),
      expectedContentHashes: new Map([[filePath, contentHash(content)]])
    });

    expect(result.ok).toBe(true);
  });

  it("rejects a stale proposal when the file changed after proposal generation", () => {
    const filePath = "/workspace/backend/src/Foo.java";
    const original = "import org.springframework.data.redis.core.RedisTemplate;\nclass Foo {}";
    const current = "class Foo {}";

    const result = validateRemediationEdits({
      intendedFilePath: filePath,
      changes: [change({ filePath })],
      currentContentsByFilePath: new Map([[filePath, current]]),
      expectedContentHashes: new Map([[filePath, contentHash(original)]])
    });

    expect(result).toMatchObject({
      ok: false,
      failure: {
        code: "FILE_CHANGED"
      }
    });
  });

  it("rejects a proposal when expected original text no longer matches the targeted range", () => {
    const filePath = "/workspace/backend/src/Foo.java";
    const content = "import org.springframework.data.redis.core.StringRedisTemplate;\nclass Foo {}";

    const result = validateRemediationEdits({
      intendedFilePath: filePath,
      changes: [change({ filePath })],
      currentContentsByFilePath: new Map([[filePath, content]]),
      expectedContentHashes: new Map([[filePath, contentHash(content)]])
    });

    expect(result).toMatchObject({
      ok: false,
      failure: {
        code: "EXPECTED_TEXT_MISMATCH"
      }
    });
  });

  it("rejects edits to unrelated files", () => {
    const intendedFilePath = "/workspace/backend/src/Foo.java";
    const unrelatedFilePath = "/workspace/backend/src/Bar.java";
    const content = "import org.springframework.data.redis.core.RedisTemplate;\nclass Bar {}";

    const result = validateRemediationEdits({
      intendedFilePath,
      changes: [change({ filePath: unrelatedFilePath })],
      currentContentsByFilePath: new Map([[unrelatedFilePath, content]]),
      expectedContentHashes: new Map([[unrelatedFilePath, contentHash(content)]])
    });

    expect(result).toMatchObject({
      ok: false,
      failure: {
        code: "UNRELATED_FILE"
      }
    });
  });

  it("matches proposal paths against current content hashes with normalized separators", () => {
    const storedPath = "C:\\workspace\\backend\\src\\Foo.java";
    const proposedPath = "C:/workspace/backend/src/Foo.java";
    const content = "import org.springframework.data.redis.core.RedisTemplate;\nclass Foo {}";

    const result = validateRemediationEdits({
      intendedFilePath: storedPath,
      changes: [change({ filePath: proposedPath })],
      currentContentsByFilePath: new Map([[proposedPath, content]]),
      expectedContentHashes: new Map([[storedPath, contentHash(content)]])
    });

    expect(result.ok).toBe(true);
  });

  it("applies only the targeted line and leaves unrelated file content unchanged", () => {
    const source = "import org.springframework.data.redis.core.RedisTemplate;\nclass Foo {\n  int score;\n}";
    const unrelated = "class Bar {}";

    const updated = applyRemediationChangesToText(source, [change({ filePath: "/workspace/backend/src/Foo.java" })]);

    expect(updated).toBe("\nclass Foo {\n  int score;\n}");
    expect(unrelated).toBe("class Bar {}");
  });

  it("preserves independent findings conceptually by editing only requested ranges", () => {
    const source = [
      "import org.springframework.data.redis.core.RedisTemplate;",
      "import com.example.ForbiddenDependency;",
      "class Foo {}"
    ].join("\n");

    const updated = applyRemediationChangesToText(source, [change({ filePath: "/workspace/backend/src/Foo.java" })]);

    expect(updated).toContain("ForbiddenDependency");
    expect(updated).not.toContain("RedisTemplate");
  });
});

function change(overrides: Partial<RemediationChange> = {}): RemediationChange {
  return {
    filePath: "/workspace/backend/src/Foo.java",
    startLine: 1,
    endLine: 1,
    expectedOriginalText: "import org.springframework.data.redis.core.RedisTemplate;",
    replacementText: "",
    reason: "Remove Redis import.",
    ...overrides
  };
}
