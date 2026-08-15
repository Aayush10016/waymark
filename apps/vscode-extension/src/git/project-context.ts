import { GitReadError } from "./git-client";

import type { GitClient } from "./git-client";
import type { GitRepositoryState, GitStatusSummary, ProjectContext } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class ProjectContextService {
  private readonly gitClient: GitClient;

  public constructor(gitClient: GitClient) {
    this.gitClient = gitClient;
  }

  public async getContext(workspaceFolder: WorkspaceFolderInfo): Promise<ProjectContext> {
    return {
      workspaceFolder,
      git: await this.getGitState(workspaceFolder)
    };
  }

  public async getContexts(workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<readonly ProjectContext[]> {
    return Promise.all(workspaceFolders.map((workspaceFolder) => this.getContext(workspaceFolder)));
  }

  private async getGitState(workspaceFolder: WorkspaceFolderInfo): Promise<GitRepositoryState> {
    try {
      const [repository, status, recentCommits] = await Promise.all([
        this.gitClient.getRepositoryInfo(workspaceFolder),
        this.gitClient.getStatus(workspaceFolder),
        this.gitClient.getRecentCommits(workspaceFolder)
      ]);

      return {
        kind: "repository",
        workspaceFolder,
        repository,
        status,
        recentCommits
      };
    } catch (error) {
      if (error instanceof GitReadError) {
        switch (error.code) {
          case "GIT_UNAVAILABLE":
            return { kind: "gitUnavailable", workspaceFolder };
          case "NOT_REPOSITORY":
            return { kind: "notRepository", workspaceFolder };
          case "READ_FAILURE":
            return {
              kind: "readFailure",
              workspaceFolder,
              message: error.message
            };
        }
      }

      return {
        kind: "readFailure",
        workspaceFolder,
        message: "Waymark was unable to read this Git repository."
      };
    }
  }
}

export function summarizeGitStatus(state: Extract<GitRepositoryState, { readonly kind: "repository" }>): GitStatusSummary {
  return {
    changedFiles: state.status.length,
    staged: state.status.filter((file) => file.staged).length,
    unstaged: state.status.filter((file) => file.unstaged).length,
    untracked: state.status.filter((file) => file.untracked).length
  };
}
