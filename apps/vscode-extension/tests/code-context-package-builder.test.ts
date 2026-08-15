import { describe, expect, it } from "vitest";

import { CodeContextBuilder } from "../src/code-context/code-context-builder";
import { buildDeterministicCodeQuery, CodeContextPackageBuilder } from "../src/code-context/code-context-package-builder";
import { GitReadError } from "../src/git/git-client";

import type { ProjectMemorySearcher } from "../src/code-context/code-context-package-builder";
import type { SourceSelection } from "../src/code-context/models";
import type { GitClient } from "../src/git/git-client";
import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "../src/git/models";
import type { ProjectMemoryDocument, ProjectMemorySearchResult } from "../src/memory/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("CodeContextPackageBuilder", () => {
  it("builds a package with separated code, project evidence, Git evidence, and bounded memory results", async () => {
    const memory = new FakeMemorySearcher(memoryResults(7));
    const git = new FakeGitClient();
    const builder = new CodeContextPackageBuilder(new CodeContextBuilder(), memory, git, {
      memoryResultLimit: 5,
      now: fixedNow
    });

    const contextPackage = await builder.build(selection());

    expect(contextPackage.codeContext.relativePath).toBe("src/AuthService.java");
    expect(contextPackage.memoryResults).toHaveLength(5);
    expect(contextPackage.projectEvidence.every((result) => ["RULE", "DECISION"].includes(result.document.sourceType))).toBe(true);
    expect(contextPackage.gitEvidence.repository).toMatchObject({ branch: "feature/auth" });
    expect(contextPackage.gitEvidence.fileStatus).toMatchObject({ path: "src/AuthService.java" });
    expect(contextPackage.gitEvidence.relevantCommits).toEqual([git.commits[0]]);
    expect(contextPackage.createdAt).toBe("2026-08-11T00:00:00.000Z");
    expect(memory.workspaceFolders).toEqual([selection().workspaceFolder]);
  });

  it("constructs deterministic query from symbol, file name, and selected identifiers", () => {
    expect(
      buildDeterministicCodeQuery({
        workspaceFolder: selection().workspaceFolder,
        filePath: "/workspace/backend/src/AuthService.java",
        relativePath: "src/AuthService.java",
        fileName: "AuthService.java",
        language: "java",
        symbolName: "authenticate",
        symbolKind: "Method",
        startLine: 1,
        endLine: 1,
        selectedText: "return jwtValidator.validate(token);",
        selectedTextPreview: "return jwtValidator.validate(token);",
        isSelectionPreviewTruncated: false,
        surroundingCode: "",
        surroundingStartLine: 1,
        surroundingEndLine: 1,
        relatedImports: []
      })
    ).toBe("authenticate AuthService jwtValidator validate token");
  });

  it("isolates memory retrieval to the selected workspace folder", async () => {
    const memory = new FakeMemorySearcher([]);
    const builder = new CodeContextPackageBuilder(new CodeContextBuilder(), memory, new FakeGitClient());
    await builder.build(selection({ workspaceFolder: { name: "extension", fsPath: "/workspace/extension" } }));

    expect(memory.workspaceFolders).toEqual([{ name: "extension", fsPath: "/workspace/extension" }]);
  });

  it("handles Git unavailable while preserving code and memory context", async () => {
    const builder = new CodeContextPackageBuilder(
      new CodeContextBuilder(),
      new FakeMemorySearcher(memoryResults(1)),
      new FakeGitClient(new GitReadError("GIT_UNAVAILABLE", "Git is unavailable."))
    );

    const contextPackage = await builder.build(selection());

    expect(contextPackage.codeContext.selectedText).toBe("authenticate(token);");
    expect(contextPackage.memoryResults).toHaveLength(1);
    expect(contextPackage.gitEvidence.unavailableReason).toBe("Git is unavailable.");
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

class FakeGitClient implements GitClient {
  public readonly commits: readonly GitCommitSummary[] = [
    {
      sha: "aaaaaaaa",
      shortSha: "a31f92c",
      subject: "Fix authentication flow",
      author: "Aayush Tiwari",
      date: "2026-08-11T00:00:00.000Z"
    },
    {
      sha: "bbbbbbbb",
      shortSha: "92bc184",
      subject: "Update unrelated docs",
      author: "Aayush Tiwari",
      date: "2026-08-10T00:00:00.000Z"
    }
  ];
  private readonly failure: GitReadError | undefined;

  public constructor(failure?: GitReadError) {
    this.failure = failure;
  }

  public async getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo> {
    await Promise.resolve();
    void workspaceFolder;
    this.throwIfConfigured();
    return {
      rootPath: "/workspace/backend",
      branch: "feature/auth",
      headCommit: "a31f92c",
      isDetachedHead: false
    };
  }

  public async getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]> {
    await Promise.resolve();
    void workspaceFolder;
    this.throwIfConfigured();
    return [status({ path: "src/AuthService.java", unstaged: true })];
  }

  public async getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]> {
    await Promise.resolve();
    void workspaceFolder;
    this.throwIfConfigured();
    return this.commits;
  }

  public async getCommitDetails(_workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<GitCommitDetail> {
    await Promise.resolve();
    return {
      sha,
      shortSha: sha.slice(0, 7),
      subject: "subject",
      author: "author",
      date: "date",
      changedFiles: sha === "aaaaaaaa" ? ["src/AuthService.java"] : ["README.md"]
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

function selection(overrides: Partial<SourceSelection> = {}): SourceSelection {
  return {
    workspaceFolder: { name: "backend", fsPath: "/workspace/backend" },
    filePath: "/workspace/backend/src/AuthService.java",
    relativePath: "src/AuthService.java",
    fileName: "AuthService.java",
    language: "java",
    fullText: "class AuthService {\n  authenticate(token);\n}",
    selectedText: "authenticate(token);",
    startLine: 2,
    endLine: 2,
    selectionStartOffset: 20,
    selectionEndOffset: 40,
    symbols: [{ name: "authenticate", kind: "Method", startLine: 2, endLine: 2 }],
    ...overrides
  };
}

function memoryResults(count: number): readonly ProjectMemorySearchResult[] {
  return Array.from({ length: count }, (_, index) => ({
    document: document(index),
    score: 100 - index,
    matchedFields: ["title"],
    matchedTerms: ["auth"]
  }));
}

function document(index: number): ProjectMemoryDocument {
  return {
    id: `doc-${index}`,
    sourceType: index % 2 === 0 ? "RULE" : "DECISION",
    title: `Auth evidence ${index}`,
    path: `/workspace/backend/.trail/rules/auth-${index}.md`,
    content: "auth",
    metadata: {},
    updatedAt: "2026-08-11T00:00:00.000Z",
    workspaceFolder: { name: "backend", fsPath: "/workspace/backend" }
  };
}

function status(overrides: Partial<GitFileStatus>): GitFileStatus {
  return {
    path: "file.ts",
    previousPath: undefined,
    staged: false,
    unstaged: false,
    untracked: false,
    deleted: false,
    renamed: false,
    ...overrides
  };
}

function fixedNow(): Date {
  return new Date("2026-08-11T00:00:00.000Z");
}
