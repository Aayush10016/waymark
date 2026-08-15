import { describe, expect, it } from "vitest";

import { GitReadError } from "../src/git/git-client";
import { ProjectContextService, summarizeGitStatus } from "../src/git/project-context";

import type { GitClient } from "../src/git/git-client";
import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "../src/git/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

const repoFolder: WorkspaceFolderInfo = {
  name: "repo",
  fsPath: "/repo"
};

const docsFolder: WorkspaceFolderInfo = {
  name: "docs",
  fsPath: "/docs"
};

describe("ProjectContextService", () => {
  it("loads Git repository state", async () => {
    const service = new ProjectContextService(new FakeGitClient());

    await expect(service.getContext(repoFolder)).resolves.toMatchObject({
      workspaceFolder: repoFolder,
      git: {
        kind: "repository",
        repository: {
          branch: "main"
        }
      }
    });
  });

  it("distinguishes non-Git directories, unavailable Git, and read failures", async () => {
    await expect(serviceForFailure("NOT_REPOSITORY").getContext(repoFolder)).resolves.toMatchObject({
      git: { kind: "notRepository" }
    });
    await expect(serviceForFailure("GIT_UNAVAILABLE").getContext(repoFolder)).resolves.toMatchObject({
      git: { kind: "gitUnavailable" }
    });
    await expect(serviceForFailure("READ_FAILURE").getContext(repoFolder)).resolves.toMatchObject({
      git: { kind: "readFailure" }
    });
  });

  it("loads multiple workspace folders independently", async () => {
    const service = new ProjectContextService(
      new FakeGitClient({
        failures: new Map([[docsFolder.fsPath, new GitReadError("NOT_REPOSITORY", "not repo")]])
      })
    );

    await expect(service.getContexts([repoFolder, docsFolder])).resolves.toMatchObject([
      {
        workspaceFolder: repoFolder,
        git: { kind: "repository" }
      },
      {
        workspaceFolder: docsFolder,
        git: { kind: "notRepository" }
      }
    ]);
  });

  it("summarizes working tree status", () => {
    expect(
      summarizeGitStatus({
        kind: "repository",
        workspaceFolder: repoFolder,
        repository: repositoryInfo,
        recentCommits: [],
        status: [
          status({ path: "staged.ts", staged: true }),
          status({ path: "unstaged.ts", unstaged: true }),
          status({ path: "untracked.ts", untracked: true })
        ]
      })
    ).toEqual({
      changedFiles: 3,
      staged: 1,
      unstaged: 1,
      untracked: 1
    });
  });
});

function serviceForFailure(code: "GIT_UNAVAILABLE" | "NOT_REPOSITORY" | "READ_FAILURE"): ProjectContextService {
  return new ProjectContextService(new FakeGitClient({ defaultFailure: new GitReadError(code, code) }));
}

const repositoryInfo: GitRepositoryInfo = {
  rootPath: "/repo",
  branch: "main",
  headCommit: "a31f92c",
  isDetachedHead: false
};

class FakeGitClient implements GitClient {
  private readonly defaultFailure: GitReadError | undefined;
  private readonly failures: ReadonlyMap<string, GitReadError>;

  public constructor(options: { readonly defaultFailure?: GitReadError; readonly failures?: ReadonlyMap<string, GitReadError> } = {}) {
    this.defaultFailure = options.defaultFailure;
    this.failures = options.failures ?? new Map();
  }

  public async getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo> {
    await Promise.resolve();
    this.throwIfConfigured(workspaceFolder);
    return repositoryInfo;
  }

  public async getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]> {
    await Promise.resolve();
    this.throwIfConfigured(workspaceFolder);
    return [status({ path: "file.ts", staged: true })];
  }

  public async getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]> {
    await Promise.resolve();
    this.throwIfConfigured(workspaceFolder);
    return [];
  }

  public async getCommitDetails(workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<GitCommitDetail> {
    await Promise.resolve();
    void workspaceFolder;
    void sha;
    throw new Error("not used");
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
    throw new Error("not used");
  }

  private throwIfConfigured(workspaceFolder: WorkspaceFolderInfo): void {
    const failure = this.failures.get(workspaceFolder.fsPath) ?? this.defaultFailure;
    if (failure !== undefined) {
      throw failure;
    }
  }
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
