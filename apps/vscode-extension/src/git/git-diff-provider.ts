import * as vscode from "vscode";

import { GitReadError } from "./git-client";

import type { GitClient } from "./git-client";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class GitRevisionContentProvider implements vscode.TextDocumentContentProvider {
  private readonly gitClient: GitClient;
  private readonly onDidChangeEmitter = new vscode.EventEmitter<vscode.Uri>();

  public readonly onDidChange = this.onDidChangeEmitter.event;

  public constructor(gitClient: GitClient) {
    this.gitClient = gitClient;
  }

  public async provideTextDocumentContent(uri: vscode.Uri): Promise<string> {
    const params = new URLSearchParams(uri.query);
    const workspaceName = params.get("workspaceName") ?? "workspace";
    const workspacePath = params.get("workspacePath");
    const filePath = params.get("filePath");
    const revision = params.get("revision") ?? "HEAD";

    if (workspacePath === null || filePath === null) {
      return "";
    }

    try {
      return await this.gitClient.getFileRevisionContent(
        {
          name: workspaceName,
          fsPath: workspacePath
        },
        filePath,
        revision
      );
    } catch (error) {
      if (error instanceof GitReadError) {
        return "";
      }

      throw error;
    }
  }

  public uri(workspaceFolder: WorkspaceFolderInfo, filePath: string, revision = "HEAD"): vscode.Uri {
    return vscode.Uri.from({
      scheme: "waymark-git",
      path: `/${filePath}`,
      query: new URLSearchParams({
        workspaceName: workspaceFolder.name,
        workspacePath: workspaceFolder.fsPath,
        filePath,
        revision
      }).toString()
    });
  }
}
