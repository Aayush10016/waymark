import { constants } from "node:fs";
import { access, readFile, readdir, stat } from "node:fs/promises";
import { basename, join, relative } from "node:path";

import { GitReadError } from "../git/git-client";
import { resolveProjectMemoryPaths } from "../workspace/project-memory-paths";

import type { GitClient } from "../git/git-client";
import type { GitFileStatus } from "../git/models";
import type { ProjectMemoryDocument, ProjectMemoryIndex, ProjectMemorySourceType } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class ProjectMemoryCollector {
  private readonly gitClient: GitClient;
  private readonly now: () => Date;

  public constructor(gitClient: GitClient, now: () => Date = () => new Date()) {
    this.gitClient = gitClient;
    this.now = now;
  }

  public async collect(workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<ProjectMemoryIndex> {
    const documents = (await Promise.all(workspaceFolders.map((folder) => this.collectWorkspace(folder)))).flat();
    return {
      documents,
      builtAt: this.now().toISOString()
    };
  }

  private async collectWorkspace(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ProjectMemoryDocument[]> {
    const documents: ProjectMemoryDocument[] = [];
    documents.push(...(await this.collectWaymarkFiles(workspaceFolder)));
    documents.push(...(await this.collectGitDocuments(workspaceFolder)));
    return documents;
  }

  private async collectWaymarkFiles(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ProjectMemoryDocument[]> {
    const paths = resolveProjectMemoryPaths(workspaceFolder);
    const documents: ProjectMemoryDocument[] = [];

    documents.push(
      ...(await this.documentFromFile(workspaceFolder, paths.projectMarkerPath, "PROJECT", "Waymark Project Marker"))
    );
    documents.push(
      ...(await this.documentFromFile(workspaceFolder, paths.constitutionPath, "CONSTITUTION", "Project Constitution"))
    );

    documents.push(...(await this.documentsFromMarkdownDirectory(workspaceFolder, paths.rulesDirectoryPath, "RULE")));
    documents.push(...(await this.documentsFromMarkdownDirectory(workspaceFolder, paths.decisionsDirectoryPath, "DECISION")));

    return documents;
  }

  private async documentFromFile(
    workspaceFolder: WorkspaceFolderInfo,
    path: string,
    sourceType: ProjectMemorySourceType,
    fallbackTitle: string
  ): Promise<readonly ProjectMemoryDocument[]> {
    if (!(await pathExists(path))) {
      return [];
    }

    const [content, fileStat] = await Promise.all([readFile(path, "utf8"), stat(path)]);
    return [
      {
        id: `${workspaceFolder.fsPath}:${sourceType}:${relative(workspaceFolder.fsPath, path)}`,
        sourceType,
        title: titleFromContent(content, fallbackTitle),
        path,
        content,
        metadata: {
          workspace: workspaceFolder.name
        },
        updatedAt: fileStat.mtime.toISOString(),
        workspaceFolder
      }
    ];
  }

  private async documentsFromMarkdownDirectory(
    workspaceFolder: WorkspaceFolderInfo,
    directoryPath: string,
    sourceType: ProjectMemorySourceType
  ): Promise<readonly ProjectMemoryDocument[]> {
    if (!(await pathExists(directoryPath))) {
      return [];
    }

    const entries = await readdir(directoryPath, { withFileTypes: true });
    const markdownFiles = entries
      .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith(".md"))
      .map((entry) => join(directoryPath, entry.name))
      .sort((left, right) => left.localeCompare(right));

    const documents = await Promise.all(
      markdownFiles.map((filePath) => this.documentFromFile(workspaceFolder, filePath, sourceType, titleFromFile(filePath)))
    );
    return documents.flat();
  }

  private async collectGitDocuments(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ProjectMemoryDocument[]> {
    try {
      const [repository, status, commits] = await Promise.all([
        this.gitClient.getRepositoryInfo(workspaceFolder),
        this.gitClient.getStatus(workspaceFolder),
        this.gitClient.getRecentCommits(workspaceFolder)
      ]);

      const updatedAt = this.now().toISOString();
      const documents: ProjectMemoryDocument[] = [
        {
          id: `${workspaceFolder.fsPath}:GIT_STATUS`,
          sourceType: "GIT_STATUS",
          title: "Current Git Working Tree",
          path: repository.rootPath,
          content: gitStatusContent(repository.branch ?? repository.headCommit, status),
          metadata: {
            workspace: workspaceFolder.name,
            branch: repository.branch ?? "",
            headCommit: repository.headCommit,
            detachedHead: repository.isDetachedHead.toString()
          },
          updatedAt,
          workspaceFolder
        },
        ...status.map((fileStatus) => gitFileDocument(workspaceFolder, fileStatus, updatedAt)),
        ...commits.map((commit) => ({
          id: `${workspaceFolder.fsPath}:GIT_COMMIT:${commit.sha}`,
          sourceType: "GIT_COMMIT" as const,
          title: commit.subject,
          path: commit.shortSha,
          content: [
            `Commit: ${commit.shortSha}`,
            `Author: ${commit.author}`,
            `Date: ${commit.date}`,
            `Subject: ${commit.subject}`
          ].join("\n"),
          metadata: {
            workspace: workspaceFolder.name,
            sha: commit.sha,
            shortSha: commit.shortSha,
            author: commit.author,
            date: commit.date
          },
          updatedAt,
          workspaceFolder
        }))
      ];

      return documents;
    } catch (error) {
      if (error instanceof GitReadError) {
        return [];
      }

      throw error;
    }
  }
}

function gitFileDocument(workspaceFolder: WorkspaceFolderInfo, fileStatus: GitFileStatus, updatedAt: string): ProjectMemoryDocument {
  const content = [
    `Path: ${fileStatus.path}`,
    `Previous path: ${fileStatus.previousPath ?? ""}`,
    `Staged: ${fileStatus.staged}`,
    `Unstaged: ${fileStatus.unstaged}`,
    `Untracked: ${fileStatus.untracked}`,
    `Deleted: ${fileStatus.deleted}`,
    `Renamed: ${fileStatus.renamed}`
  ].join("\n");

  return {
    id: `${workspaceFolder.fsPath}:GIT_FILE:${fileStatus.path}`,
    sourceType: "GIT_FILE",
    title: fileStatus.path,
    path: fileStatus.path,
    content,
    metadata: {
      workspace: workspaceFolder.name,
      previousPath: fileStatus.previousPath ?? "",
      staged: fileStatus.staged.toString(),
      unstaged: fileStatus.unstaged.toString(),
      untracked: fileStatus.untracked.toString(),
      deleted: fileStatus.deleted.toString(),
      renamed: fileStatus.renamed.toString()
    },
    updatedAt,
    workspaceFolder
  };
}

function gitStatusContent(branchOrHead: string, status: readonly GitFileStatus[]): string {
  return [
    `Branch: ${branchOrHead}`,
    "",
    "Staged:",
    ...status.filter((file) => file.staged).map((file) => file.path),
    "",
    "Unstaged:",
    ...status.filter((file) => file.unstaged).map((file) => file.path),
    "",
    "Untracked:",
    ...status.filter((file) => file.untracked).map((file) => file.path)
  ].join("\n");
}

function titleFromContent(content: string, fallbackTitle: string): string {
  const firstHeading = content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.startsWith("# "));
  return firstHeading?.replace(/^#\s+/, "").trim() || fallbackTitle;
}

function titleFromFile(path: string): string {
  return basename(path, ".md")
    .split(/[-_]+/)
    .filter((part) => part.length > 0)
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join(" ");
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch (error) {
    if (isNodeError(error) && error.code === "ENOENT") {
      return false;
    }

    throw error;
  }
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}
