import { describe, expect, it } from "vitest";

import { AutomaticGuardianRunner } from "../src/guardian/automatic-guardian-runner";
import { DebouncedFileChangeQueue } from "../src/guardian/debounced-file-change-queue";
import { isAutomaticGuardianRelevantPath } from "../src/guardian/file-relevance";

import type { ProjectMemorySearcher } from "../src/code-context/code-context-package-builder";
import type { ProjectMemorySearchResult } from "../src/memory/models";
import type { ArchitecturalDecision } from "../src/project-source/decisions";
import type { ExplicitRule } from "../src/project-source/rules";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";
import type { GuardianFileContent } from "../src/guardian/file-content-context";

describe("AutomaticGuardianRunner", () => {
  it("detects RedisTemplate in changed Java file content and clears after the content is fixed", async () => {
    const runner = automaticRunner([
      rule({
        id: "no-redis",
        title: "Redis is not allowed",
        scope: "backend",
        ruleText: "Redis must not be used anywhere in the backend."
      })
    ]);

    const withRedis = await runner.checkFiles([
      file({
        relativePath: "backend/src/main/java/ArcadeScoreController.java",
        content: "import org.springframework.data.redis.core.RedisTemplate;\nclass ArcadeScoreController {}"
      })
    ]);
    const withoutRedis = await runner.checkFiles([
      file({
        relativePath: "backend/src/main/java/ArcadeScoreController.java",
        content: "class ArcadeScoreController {}"
      })
    ]);

    expect(withRedis.findings).toHaveLength(1);
    expect(withRedis.findings[0]?.ruleTitle).toBe("Redis is not allowed");
    expect(withRedis.findings[0]?.codePath).toBe("/workspace/backend/src/main/java/ArcadeScoreController.java");
    expect(withoutRedis.findings).toEqual([]);
  });

  it("coalesces multiple changed files through the debounce queue", () => {
    const flushed: string[][] = [];
    const scheduledCallbacks: Array<() => void> = [];
    const queue = new DebouncedFileChangeQueue<string>({
      delayMs: 750,
      setTimer: (callback) => {
        scheduledCallbacks.push(callback);
        return callback;
      },
      clearTimer: () => undefined,
      onFlush: (items) => {
        flushed.push([...items]);
      },
      keyFor: (item) => item
    });

    queue.enqueue("A.java");
    queue.enqueue("B.java");
    queue.enqueue("A.java");
    scheduledCallbacks.at(-1)?.();

    expect(flushed).toEqual([["A.java", "B.java"]]);
  });

  it("respects backend scope and does not flag frontend files", async () => {
    const runner = automaticRunner([
      rule({
        scope: "backend",
        title: "Redis is not allowed",
        ruleText: "Redis must not be used anywhere in the backend."
      })
    ]);

    const result = await runner.checkFiles([
      file({
        relativePath: "frontend/src/ArcadeScore.tsx",
        content: "const value = 'RedisTemplate';"
      })
    ]);

    expect(result.findings).toEqual([]);
  });

  it("ignores deprecated rules", async () => {
    const runner = automaticRunner([
      rule({
        status: "deprecated",
        isActive: false,
        title: "Redis is not allowed",
        ruleText: "Redis must not be used anywhere."
      })
    ]);

    const result = await runner.checkFiles([
      file({ content: "RedisTemplate redisTemplate;" })
    ]);

    expect(result.findings).toEqual([]);
  });

  it("works without Git because file content is the trigger", async () => {
    const runner = automaticRunner([
      rule({
        title: "Redis is not allowed",
        ruleText: "Redis must not be used anywhere."
      })
    ]);

    const result = await runner.checkFiles([
      file({ content: "RedisTemplate redisTemplate;" })
    ]);

    expect(result.findings).toHaveLength(1);
  });

  it("ignores unsupported files and projects with no active rules", async () => {
    const unsupported = await automaticRunner([
      rule({ title: "Redis is not allowed", ruleText: "Redis must not be used anywhere." })
    ]).checkFiles([
      file({ relativePath: "backend/README.md", content: "RedisTemplate" })
    ]);
    const noRules = await automaticRunner([]).checkFiles([
      file({ content: "RedisTemplate redisTemplate;" })
    ]);

    expect(unsupported.findings).toEqual([]);
    expect(noRules.findings).toEqual([]);
  });

  it("keeps Project Memory retrieval workspace-local", async () => {
    const memory = new FakeMemorySearcher([]);
    const runner = automaticRunner([
      rule({
        title: "Redis is not allowed",
        ruleText: "Redis must not be used anywhere."
      })
    ], [], memory);

    await runner.checkFiles([file({ content: "RedisTemplate redisTemplate;" })]);

    expect(memory.workspaceFolders).toEqual([workspace()]);
  });
});

describe("isAutomaticGuardianRelevantPath", () => {
  it("supports conservative source and configuration file types", () => {
    expect(isAutomaticGuardianRelevantPath("src/Foo.java")).toBe(true);
    expect(isAutomaticGuardianRelevantPath("src/Foo.tsx")).toBe(true);
    expect(isAutomaticGuardianRelevantPath("application.yml")).toBe(true);
    expect(isAutomaticGuardianRelevantPath("README.md")).toBe(false);
    expect(isAutomaticGuardianRelevantPath("node_modules/pkg/index.ts")).toBe(false);
  });
});

function automaticRunner(
  rules: readonly ExplicitRule[],
  decisions: readonly ArchitecturalDecision[] = [],
  memory: ProjectMemorySearcher = new FakeMemorySearcher([])
): AutomaticGuardianRunner {
  return new AutomaticGuardianRunner(
    new FakeRuleReader(rules),
    new FakeDecisionReader(decisions),
    memory
  );
}

class FakeRuleReader {
  private readonly rules: readonly ExplicitRule[];

  public constructor(rules: readonly ExplicitRule[]) {
    this.rules = rules;
  }

  public async list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ExplicitRule[]> {
    await Promise.resolve();
    void workspaceFolder;
    return this.rules;
  }
}

class FakeDecisionReader {
  private readonly decisions: readonly ArchitecturalDecision[];

  public constructor(decisions: readonly ArchitecturalDecision[]) {
    this.decisions = decisions;
  }

  public async list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ArchitecturalDecision[]> {
    await Promise.resolve();
    void workspaceFolder;
    return this.decisions;
  }
}

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

function file(overrides: Partial<GuardianFileContent> = {}): GuardianFileContent {
  return {
    workspaceFolder: workspace(),
    filePath: "/workspace/backend/src/main/java/ArcadeScoreController.java",
    relativePath: "backend/src/main/java/ArcadeScoreController.java",
    content: "class ArcadeScoreController {}",
    ...overrides
  };
}

function rule(overrides: Partial<ExplicitRule> = {}): ExplicitRule {
  return {
    id: "rule-1",
    title: "Rule",
    status: "active",
    type: "technology",
    rawStatus: "active",
    rawType: "technology",
    scope: "project-wide",
    ruleText: "Redis must not be used anywhere.",
    reason: "",
    source: "",
    path: "/workspace/.trail/rules/no-redis.md",
    isActive: true,
    hasMetadataWarning: false,
    ...overrides
  };
}

function workspace(): WorkspaceFolderInfo {
  return {
    name: "workspace",
    fsPath: "/workspace"
  };
}
