import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { ProjectMemoryCollector } from "../src/memory/memory-collector";
import { ProjectMemoryRepository } from "../src/memory/memory-repository";
import { DeterministicMemoryRetriever } from "../src/memory/memory-retriever";

import type { GitClient } from "../src/git/git-client";
import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "../src/git/models";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

const tempRoots: string[] = [];

describe("ProjectMemoryCollector", () => {
  afterEach(cleanup);

  it("does not reuse cached evidence after switching workspace folders", async () => {
    const first = await createWorkspace("first");
    const second = await createWorkspace("second");
    const repository = new ProjectMemoryRepository(new ProjectMemoryCollector(new FakeGitClient(), fixedNow), new DeterministicMemoryRetriever());
    await repository.search("PostgreSQL", [first]);
    const results = await repository.search("PostgreSQL", [second]);
    expect(results.length).toBeGreaterThan(0);
    expect(results.every(result => result.document.workspaceFolder.fsPath === second.fsPath)).toBe(true);
  });

  it("collects project marker, constitution, rules, decisions, Git status, Git files, and recent commits", async () => {
    const workspace = await createWorkspace("backend");
    const collector = new ProjectMemoryCollector(new FakeGitClient(), fixedNow);

    const index = await collector.collect([workspace]);

    expect(index.documents.map((document) => document.sourceType)).toEqual([
      "PROJECT",
      "CONSTITUTION",
      "RULE",
      "DECISION",
      "GIT_STATUS",
      "GIT_FILE",
      "GIT_COMMIT"
    ]);
    expect(index.documents.find((document) => document.sourceType === "GIT_COMMIT")).toMatchObject({
      title: "Configure PostgreSQL datasource",
      metadata: {
        shortSha: "a31f92c"
      }
    });
  });

  it("keeps separate workspace memories distinguishable", async () => {
    const backend = await createWorkspace("backend");
    const extension = await createWorkspace("extension");
    const collector = new ProjectMemoryCollector(new FakeGitClient(), fixedNow);

    const index = await collector.collect([backend, extension]);

    expect([...new Set(index.documents.map((document) => document.workspaceFolder.name))].sort()).toEqual([
      "backend",
      "extension"
    ]);
  });

  it("reflects changed constitution content after refresh", async () => {
    const workspace = await createWorkspace("backend");
    const repository = new ProjectMemoryRepository(
      new ProjectMemoryCollector(new FakeGitClient(), fixedNow),
      new DeterministicMemoryRetriever()
    );

    await expect(repository.search("PostgreSQL", [workspace])).resolves.toHaveLength(4);

    await writeFile(join(workspace.fsPath, ".waymark", "constitution.md"), "# Project Constitution\n\nSQLite only\n", "utf8");
    await repository.refresh([workspace]);

    await expect(repository.search("SQLite", [workspace])).resolves.toHaveLength(1);
  });

  it("reflects added rules and decisions after refresh", async () => {
    const workspace = await createWorkspace("backend");
    const repository = new ProjectMemoryRepository(
      new ProjectMemoryCollector(new FakeGitClient(), fixedNow),
      new DeterministicMemoryRetriever()
    );

    await writeFile(join(workspace.fsPath, ".waymark", "rules", "api-contracts.md"), "# API Contracts\n", "utf8");
    await writeFile(join(workspace.fsPath, ".waymark", "decisions", "ADR-002-api-contracts.md"), "# ADR-002: API Contracts\n", "utf8");
    await repository.refresh([workspace]);

    await expect(repository.search("contracts", [workspace])).resolves.toHaveLength(2);
  });

  it("reflects Git refresh updates", async () => {
    const workspace = await createWorkspace("backend");
    const gitClient = new FakeGitClient();
    const repository = new ProjectMemoryRepository(
      new ProjectMemoryCollector(gitClient, fixedNow),
      new DeterministicMemoryRetriever()
    );

    await expect(repository.search("AuthService", [workspace])).resolves.toHaveLength(2);
    gitClient.status = [status({ path: "PaymentService.java", unstaged: true })];
    await repository.refresh([workspace]);

    await expect(repository.search("PaymentService", [workspace])).resolves.toHaveLength(2);
  });

  it("does not scan source-tree files outside .waymark", async () => {
    const workspace = await createWorkspace("backend");
    await writeFile(join(workspace.fsPath, "SecretArchitecture.java"), "class SecretArchitecture {}", "utf8");
    const collector = new ProjectMemoryCollector(new FakeGitClient(), fixedNow);

    const index = await collector.collect([workspace]);

    expect(index.documents.some((document) => document.content.includes("SecretArchitecture"))).toBe(false);
  });

  it("collects legacy .trail project memory when no canonical .waymark directory exists", async () => {
    const workspace = await createWorkspace("backend", ".trail");
    const collector = new ProjectMemoryCollector(new FakeGitClient(), fixedNow);

    const index = await collector.collect([workspace]);

    expect(index.documents.some((document) => document.path?.startsWith(join(workspace.fsPath, ".trail")))).toBe(true);
    expect(index.documents.map((document) => document.sourceType)).toEqual(expect.arrayContaining([
      "PROJECT",
      "CONSTITUTION",
      "RULE",
      "DECISION"
    ]));
  });
});

async function createWorkspace(name: string, directoryName: ".waymark" | ".trail" = ".waymark"): Promise<WorkspaceFolderInfo> {
  const root = await mkdtemp(join(tmpdir(), "trail-vscode-extension-"));
  tempRoots.push(root);
  const workspacePath = join(root, name);
  await mkdir(join(workspacePath, directoryName, "rules"), { recursive: true });
  await mkdir(join(workspacePath, directoryName, "decisions"), { recursive: true });
  await writeFile(
    join(workspacePath, directoryName, "project.json"),
    JSON.stringify({ schemaVersion: 1, projectId: `${name}-id`, name }, null, 2),
    "utf8"
  );
  await writeFile(join(workspacePath, directoryName, "constitution.md"), "# Project Constitution\n\nPostgreSQL\n", "utf8");
  await writeFile(join(workspacePath, directoryName, "rules", "database-policy.md"), "# Database Policy\n\nPostgreSQL\n", "utf8");
  await writeFile(join(workspacePath, directoryName, "decisions", "ADR-001-use-postgresql.md"), "# ADR-001: Use PostgreSQL\n", "utf8");
  return {
    name,
    fsPath: workspacePath
  };
}

async function cleanup(): Promise<void> {
  await Promise.all(tempRoots.map((path) => rm(path, { recursive: true, force: true })));
  tempRoots.length = 0;
}

function fixedNow(): Date {
  return new Date("2026-08-11T00:00:00.000Z");
}

class FakeGitClient implements GitClient {
  public status: readonly GitFileStatus[] = [status({ path: "AuthService.java", staged: true })];

  public async getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo> {
    await Promise.resolve();
    return {
      rootPath: workspaceFolder.fsPath,
      branch: "feature/auth",
      headCommit: "a31f92c",
      isDetachedHead: false
    };
  }

  public async getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]> {
    await Promise.resolve();
    void workspaceFolder;
    return this.status;
  }

  public async getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]> {
    await Promise.resolve();
    void workspaceFolder;
    return [
      {
        sha: "aaaaaaaa",
        shortSha: "a31f92c",
        subject: "Configure PostgreSQL datasource",
        author: "Aayush Tiwari",
        date: "2026-08-11T00:00:00.000Z"
      }
    ];
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
