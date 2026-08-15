import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export interface GitRepositoryInfo {
  readonly rootPath: string;
  readonly branch: string | undefined;
  readonly headCommit: string;
  readonly isDetachedHead: boolean;
}

export interface GitFileStatus {
  readonly path: string;
  readonly previousPath: string | undefined;
  readonly staged: boolean;
  readonly unstaged: boolean;
  readonly untracked: boolean;
  readonly deleted: boolean;
  readonly renamed: boolean;
}

export interface GitCommitSummary {
  readonly sha: string;
  readonly shortSha: string;
  readonly subject: string;
  readonly author: string;
  readonly date: string;
}

export interface GitCommitDetail {
  readonly sha: string;
  readonly shortSha: string;
  readonly subject: string;
  readonly author: string;
  readonly date: string;
  readonly changedFiles: readonly string[];
}

export type GitRepositoryState =
  | {
      readonly kind: "repository";
      readonly workspaceFolder: WorkspaceFolderInfo;
      readonly repository: GitRepositoryInfo;
      readonly status: readonly GitFileStatus[];
      readonly recentCommits: readonly GitCommitSummary[];
    }
  | {
      readonly kind: "notRepository";
      readonly workspaceFolder: WorkspaceFolderInfo;
    }
  | {
      readonly kind: "gitUnavailable";
      readonly workspaceFolder: WorkspaceFolderInfo;
    }
  | {
      readonly kind: "readFailure";
      readonly workspaceFolder: WorkspaceFolderInfo;
      readonly message: string;
    };

export interface ProjectContext {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly git: GitRepositoryState;
}

export interface GitStatusSummary {
  readonly changedFiles: number;
  readonly staged: number;
  readonly unstaged: number;
  readonly untracked: number;
}
