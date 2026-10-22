import { describe, expect, it } from "vitest";

import { GitReadError } from "../src/git/git-client";
import { GuardianContextBuilder } from "../src/guardian/guardian-context-builder";

import type { CodeContextPackage } from "../src/code-context/models";
import type { GitClient } from "../src/git/git-client";
import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "../src/git/models";
import type { ProjectMemorySearcher } from "../src/code-context/code-context-package-builder";
import type { ProjectMemorySearchResult } from "../src/memory/models";
import type { GuardianDecisionReader, GuardianRuleReader } from "../src/guardian/guardian-context-builder";
import type { ArchitecturalDecision } from "../src/project-source/decisions";
import type { ExplicitRule } from "../src/project-source/rules";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("GuardianContextBuilder", () => {
  it("builds selected-code context from active rules, accepted decisions, and CodeContextPackage memory", async () => {
    const memory = new FakeMemorySearcher([memoryResult("unexpected")]);
    const builder = new GuardianContextBuilder(
      new FakeRuleStore([rule({ id: "active" }), rule({ id: "deprecated", status: "deprecated", isActive: false })]),
      new FakeDecisionStore([
        decision({ title: "Accepted PostgreSQL", status: "Accepted" }),
        decision({ title: "Proposed MongoDB", status: "Proposed" })
      ]),
      memory,
      new FakeGitClient()
    );

    const context = await builder.buildForCodePackage(codePackage([memoryResult("package-memory")]));

    expect(context.activeRules.map((item) => item.id)).toEqual(["active"]);
    expect(context.relevantDecisions.map((item) => item.title)).toEqual(["Accepted PostgreSQL"]);
    expect(context.relevantProjectMemory.map((item) => item.document.id)).toEqual(["package-memory"]);
    expect(memory.searchCalls).toBe(0);
  });

  it("builds current-change context from read-only Git status and existing Project Memory", async () => {
    const memory = new FakeMemorySearcher([memoryResult("change-memory")]);
    const builder = new GuardianContextBuilder(
      new FakeRuleStore([rule()]),
      new FakeDecisionStore([decision()]),
      memory,
      new FakeGitClient([status({ path: "apps/backend/src/mongodb/repository.ts" })])
    );

    const result = await builder.buildForCurrentChanges(workspace());

    expect(result.kind).toBe("ready");
    if (result.kind === "ready") {
      expect(result.context.changedFiles.map((file) => file.path)).toEqual(["apps/backend/src/mongodb/repository.ts"]);
      expect(result.context.activeRules).toHaveLength(1);
      expect(result.context.relevantProjectMemory).toHaveLength(1);
    }
    expect(memory.lastQuery).toBe("apps/backend/src/mongodb/repository.ts");
    expect(memory.lastWorkspaceFolders).toEqual([workspace()]);
  });

  it("handles no current Git changes", async () => {
    const builder = new GuardianContextBuilder(
      new FakeRuleStore([]),
      new FakeDecisionStore([]),
      new FakeMemorySearcher([]),
      new FakeGitClient([])
    );

    await expect(builder.buildForCurrentChanges(workspace())).resolves.toEqual({ kind: "noChanges" });
  });

  it("handles Git unavailable without crashing", async () => {
    const builder = new GuardianContextBuilder(
      new FakeRuleStore([]),
      new FakeDecisionStore([]),
      new FakeMemorySearcher([]),
      new FakeGitClient([], new GitReadError("NOT_REPOSITORY", "not a repository"))
    );

    await expect(builder.buildForCurrentChanges(workspace())).resolves.toEqual({
      kind: "gitUnavailable",
      message: "Waymark Guardian requires a Git repository for current-change checking."
    });
  });
});

class FakeRuleStore implements GuardianRuleReader {
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

class FakeDecisionStore implements GuardianDecisionReader {
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
  public searchCalls = 0;
  public lastQuery = "";
  public lastWorkspaceFolders: readonly WorkspaceFolderInfo[] = [];
  private readonly results: readonly ProjectMemorySearchResult[];

  public constructor(results: readonly ProjectMemorySearchResult[]) {
    this.results = results;
  }

  public async search(query: string, workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<readonly ProjectMemorySearchResult[]> {
    await Promise.resolve();
    this.searchCalls += 1;
    this.lastQuery = query;
    this.lastWorkspaceFolders = workspaceFolders;
    return this.results;
  }
}

class FakeGitClient implements GitClient {
  private readonly changedFiles: readonly GitFileStatus[];
  private readonly failure: GitReadError | undefined;

  public constructor(changedFiles: readonly GitFileStatus[] = [], failure?: GitReadError) {
    this.changedFiles = changedFiles;
    this.failure = failure;
  }

  public async getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo> {
    await Promise.resolve();
    void workspaceFolder;
    this.throwIfConfigured();
    return {
      rootPath: "/workspace",
      branch: "main",
      headCommit: "abc123",
      isDetachedHead: false
    };
  }

  public async getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]> {
    await Promise.resolve();
    void workspaceFolder;
    this.throwIfConfigured();
    return this.changedFiles;
  }

  public async getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]> {
    await Promise.resolve();
    void workspaceFolder;
    return [];
  }

  public async getCommitDetails(workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<GitCommitDetail> {
    await Promise.resolve();
    void workspaceFolder;
    return {
      sha,
      shortSha: sha.slice(0, 7),
      subject: "",
      author: "",
      date: "",
      changedFiles: []
    };
  }

  public async getFileRevisionContent(
    workspaceFolder: WorkspaceFolderInfo,
    filePath: string,
    revision: string
  ): Promise<string> {
    await Promise.resolve();
    void workspaceFolder;
    void filePath;
    void revision;
    return "";
  }

  private throwIfConfigured(): void {
    if (this.failure !== undefined) {
      throw this.failure;
    }
  }
}

function codePackage(memoryResults: readonly ProjectMemorySearchResult[] = []): CodeContextPackage {
  return {
    codeContext: {
      workspaceFolder: workspace(),
      filePath: "/workspace/apps/backend/src/AuthController.java",
      relativePath: "apps/backend/src/AuthController.java",
      fileName: "AuthController.java",
      language: "java",
      symbolName: "createSession",
      symbolKind: "Method",
      startLine: 10,
      endLine: 14,
      selectedText: "return authService.authenticate(request);",
      selectedTextPreview: "return authService.authenticate(request);",
      isSelectionPreviewTruncated: false,
      surroundingCode: "",
      surroundingStartLine: 8,
      surroundingEndLine: 16,
      relatedImports: []
    },
    projectEvidence: [],
    gitEvidence: {
      repository: undefined,
      fileStatus: undefined,
      relevantCommits: [],
      unavailableReason: undefined
    },
    memoryResults,
    createdAt: "2026-08-11T00:00:00.000Z"
  };
}

function rule(overrides: Partial<ExplicitRule> = {}): ExplicitRule {
  return {
    id: "rule-1",
    title: "No MongoDB",
    status: "active",
    type: "technology",
    rawStatus: "active",
    rawType: "technology",
    scope: "project-wide",
    ruleText: "Do not use MongoDB.",
    reason: "",
    source: "",
    path: "/workspace/.waymark/rules/no-mongodb.md",
    isActive: true,
    hasMetadataWarning: false,
    ...overrides
  };
}

function decision(overrides: Partial<ArchitecturalDecision> = {}): ArchitecturalDecision {
  return {
    id: "ADR-001",
    title: "Accepted PostgreSQL",
    status: "Accepted",
    rawStatus: "Accepted",
    path: "/workspace/.waymark/decisions/ADR-001.md",
    hasMetadataWarning: false,
    ...overrides
  };
}

function memoryResult(id: string): ProjectMemorySearchResult {
  return {
    document: {
      id,
      sourceType: "RULE",
      title: "No MongoDB",
      path: "/workspace/.waymark/rules/no-mongodb.md",
      content: "Do not use MongoDB.",
      metadata: {},
      updatedAt: "2026-08-11T00:00:00.000Z",
      workspaceFolder: workspace()
    },
    score: 20,
    matchedFields: ["content"],
    matchedTerms: ["mongodb"]
  };
}

function status(overrides: Partial<GitFileStatus> = {}): GitFileStatus {
  return {
    path: "apps/backend/src/file.ts",
    previousPath: undefined,
    staged: false,
    unstaged: true,
    untracked: false,
    deleted: false,
    renamed: false,
    ...overrides
  };
}

function workspace(): WorkspaceFolderInfo {
  return {
    name: "trail",
    fsPath: "/workspace"
  };
}
