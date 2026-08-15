import { execFile } from "node:child_process";
import { promisify } from "node:util";

import {
  parseCommitDetail,
  parsePorcelainStatus,
  parseRecentCommits,
  parseRepositoryInfo
} from "./git-parsers";

import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

const execFileAsync = promisify(execFile);
const RECENT_COMMIT_LIMIT = "10";
const COMMIT_FORMAT = "%H%x1f%h%x1f%s%x1f%an%x1f%aI%x1e";
const COMMIT_DETAIL_FORMAT = "%H%x1f%h%x1f%s%x1f%an%x1f%aI";

export type GitReadErrorCode = "GIT_UNAVAILABLE" | "NOT_REPOSITORY" | "READ_FAILURE";

export class GitReadError extends Error {
  public constructor(
    public readonly code: GitReadErrorCode,
    message: string
  ) {
    super(message);
    this.name = "GitReadError";
  }
}

export interface GitClient {
  getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo>;
  getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]>;
  getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]>;
  getCommitDetails(workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<GitCommitDetail>;
  getFileRevisionContent(workspaceFolder: WorkspaceFolderInfo, filePath: string, revision: string): Promise<string>;
}

export interface GitProcessRunner {
  run(workspaceFolder: WorkspaceFolderInfo, args: readonly string[]): Promise<string>;
}

export class GitCliClient implements GitClient {
  private readonly runner: GitProcessRunner;

  public constructor(runner: GitProcessRunner = new ExecFileGitProcessRunner()) {
    this.runner = runner;
  }

  public async getRepositoryInfo(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryInfo> {
    const isRepository = await this.runner.run(workspaceFolder, ["rev-parse", "--is-inside-work-tree"]);
    if (isRepository.trim() !== "true") {
      throw new GitReadError("NOT_REPOSITORY", "This workspace folder is not a Git repository.");
    }

    const [rootPath, branch, headCommit] = await Promise.all([
      this.runner.run(workspaceFolder, ["rev-parse", "--show-toplevel"]),
      this.runner.run(workspaceFolder, ["rev-parse", "--abbrev-ref", "HEAD"]),
      this.runner.run(workspaceFolder, ["rev-parse", "--short", "HEAD"])
    ]);

    return parseRepositoryInfo(rootPath.trim(), branch, headCommit);
  }

  public async getStatus(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitFileStatus[]> {
    return parsePorcelainStatus(await this.runner.run(workspaceFolder, ["status", "--porcelain=v1", "-z"]));
  }

  public async getRecentCommits(workspaceFolder: WorkspaceFolderInfo): Promise<readonly GitCommitSummary[]> {
    return parseRecentCommits(
      await this.runner.run(workspaceFolder, ["log", "-n", RECENT_COMMIT_LIMIT, `--pretty=format:${COMMIT_FORMAT}`])
    );
  }

  public async getCommitDetails(workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<GitCommitDetail> {
    validateGitObjectReference(sha);
    return parseCommitDetail(
      await this.runner.run(workspaceFolder, ["show", "--name-only", `--format=${COMMIT_DETAIL_FORMAT}`, "--no-renames", sha])
    );
  }

  public async getFileRevisionContent(
    workspaceFolder: WorkspaceFolderInfo,
    filePath: string,
    revision: string
  ): Promise<string> {
    validateGitObjectReference(revision);
    validateRelativeGitPath(filePath);
    return this.runner.run(workspaceFolder, ["show", `${revision}:${filePath}`]);
  }
}

export class ExecFileGitProcessRunner implements GitProcessRunner {
  public async run(workspaceFolder: WorkspaceFolderInfo, args: readonly string[]): Promise<string> {
    assertReadOnlyGitArgs(args);

    try {
      const result = await execFileAsync("git", [...args], {
        cwd: workspaceFolder.fsPath,
        encoding: "utf8",
        maxBuffer: 1024 * 1024,
        shell: false,
        windowsHide: true
      });
      return result.stdout;
    } catch (error) {
      throw toGitReadError(error);
    }
  }
}

export function assertReadOnlyGitArgs(args: readonly string[]): void {
  const operation = args[0];
  const allowedOperations = new Set(["rev-parse", "status", "log", "show"]);
  if (operation === undefined || !allowedOperations.has(operation)) {
    throw new GitReadError("READ_FAILURE", "Waymark attempted an unsupported Git operation.");
  }
}

function toGitReadError(error: unknown): GitReadError {
  if (isNodeError(error) && error.code === "ENOENT") {
    return new GitReadError("GIT_UNAVAILABLE", "Git is unavailable.");
  }

  const stderr = isExecFileError(error) ? error.stderr : "";
  if (stderr.toLowerCase().includes("not a git repository")) {
    return new GitReadError("NOT_REPOSITORY", "This workspace folder is not a Git repository.");
  }

  return new GitReadError("READ_FAILURE", "Waymark was unable to read this Git repository.");
}

function validateGitObjectReference(value: string): void {
  if (!/^[a-zA-Z0-9._/^~-]+$/.test(value)) {
    throw new GitReadError("READ_FAILURE", "Invalid Git object reference.");
  }
}

function validateRelativeGitPath(value: string): void {
  if (value.length === 0 || value.startsWith("/") || value.includes("\0") || value.includes("..")) {
    throw new GitReadError("READ_FAILURE", "Invalid Git file path.");
  }
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}

function isExecFileError(error: unknown): error is Error & { readonly stderr: string } {
  return error instanceof Error && "stderr" in error && typeof error.stderr === "string";
}
