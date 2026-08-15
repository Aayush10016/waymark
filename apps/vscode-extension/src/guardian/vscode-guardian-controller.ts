import * as vscode from "vscode";

import { activeSourceSelection } from "../code-context/vscode-code-context";
import { UserFacingError } from "../shared/errors";
import { formatGuardianCheckResult, formatGuardianFindingDetail } from "./guardian-presenter";

import type { CodeContextPackageBuilder } from "../code-context/code-context-package-builder";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";
import type { DeterministicGuardianChecker } from "./guardian-checker";
import type { GuardianContextBuilder } from "./guardian-context-builder";
import type { GuardianCheckResult, GuardianFinding } from "./models";

export interface GuardianFindingSink {
  setGuardianFindings(findings: readonly GuardianFinding[]): void;
}

export interface GuardianRemediationHandler {
  suggestFix(finding: GuardianFinding): Promise<void>;
}

export class VsCodeGuardianController {
  private readonly codeContextPackageBuilder: CodeContextPackageBuilder;
  private readonly contextBuilder: GuardianContextBuilder;
  private readonly checker: DeterministicGuardianChecker;
  private readonly outputChannel: vscode.OutputChannel;
  private readonly findingSink: GuardianFindingSink;
  private readonly remediationHandler: GuardianRemediationHandler | undefined;

  public constructor(
    codeContextPackageBuilder: CodeContextPackageBuilder,
    contextBuilder: GuardianContextBuilder,
    checker: DeterministicGuardianChecker,
    outputChannel: vscode.OutputChannel,
    findingSink: GuardianFindingSink,
    remediationHandler?: GuardianRemediationHandler
  ) {
    this.codeContextPackageBuilder = codeContextPackageBuilder;
    this.contextBuilder = contextBuilder;
    this.checker = checker;
    this.outputChannel = outputChannel;
    this.findingSink = findingSink;
    this.remediationHandler = remediationHandler;
  }

  public async checkSelectedCode(): Promise<void> {
    const selection = await activeSourceSelection();
    const codeContextPackage = await this.codeContextPackageBuilder.build(selection);
    const guardianContext = await this.contextBuilder.buildForCodePackage(codeContextPackage);
    const result = this.checker.check(guardianContext);
    this.publishResult(result);

    if (result.findings.length === 0) {
      await vscode.window.showInformationMessage("Waymark Guardian found no deterministic project-rule conflicts.");
    } else {
      await vscode.window.showWarningMessage(`Waymark Guardian found ${result.findings.length} potential project-rule conflict(s).`);
    }
  }

  public async checkCurrentChanges(): Promise<void> {
    const workspaceFolder = await this.resolveWorkspaceFolder("Select a workspace folder to check current changes");
    if (workspaceFolder === undefined) {
      return;
    }

    const result = await this.contextBuilder.buildForCurrentChanges(workspaceFolder);
    switch (result.kind) {
      case "noChanges":
        this.findingSink.setGuardianFindings([]);
        await vscode.window.showInformationMessage("No current Git changes to check.");
        return;
      case "gitUnavailable":
        this.findingSink.setGuardianFindings([]);
        await vscode.window.showInformationMessage(result.message);
        return;
      case "ready": {
        const checkResult = this.checker.check(result.context);
        this.publishResult(checkResult);
        if (checkResult.findings.length === 0) {
          await vscode.window.showInformationMessage("Waymark Guardian found no deterministic project-rule conflicts.");
        } else {
          await vscode.window.showWarningMessage(`Waymark Guardian found ${checkResult.findings.length} potential project-rule conflict(s).`);
        }
      }
    }
  }

  public async showFindingDetail(finding: GuardianFinding): Promise<void> {
    const actions = this.findingActions(finding);
    const action = await vscode.window.showInformationMessage(
      formatGuardianFindingDetail(finding),
      { modal: true },
      ...actions
    );

    if (action === "Suggest Fix") {
      await this.remediationHandler?.suggestFix(finding);
      return;
    }

    if (action === "Open Rule") {
      await vscode.window.showTextDocument(vscode.Uri.file(finding.rulePath));
      return;
    }

    if (action === "Open Code" && finding.codePath !== undefined) {
      const document = await vscode.workspace.openTextDocument(vscode.Uri.file(finding.codePath));
      await vscode.window.showTextDocument(document);
    }
  }

  private findingActions(finding: GuardianFinding): readonly string[] {
    if (finding.codePath === undefined) {
      return ["Open Rule"];
    }

    return this.remediationHandler === undefined
      ? ["Open Rule", "Open Code"]
      : ["Suggest Fix", "Open Rule", "Open Code"];
  }

  private publishResult(result: GuardianCheckResult): void {
    this.findingSink.setGuardianFindings(result.findings);
    this.outputChannel.clear();
    this.outputChannel.appendLine(formatGuardianCheckResult(result));
    this.outputChannel.show(true);
  }

  private async resolveWorkspaceFolder(placeHolder: string): Promise<WorkspaceFolderInfo | undefined> {
    const activeWorkspace = this.activeWorkspaceFolder();
    const folders = vscode.workspace.workspaceFolders ?? [];
    if (activeWorkspace !== undefined) {
      return activeWorkspace;
    }
    if (folders.length === 0) {
      throw new UserFacingError("Open a workspace before using Waymark Guardian.");
    }
    if (folders.length === 1) {
      const folder = folders[0];
      return folder === undefined ? undefined : { name: folder.name, fsPath: folder.uri.fsPath };
    }

    const selected = await vscode.window.showQuickPick(
      folders.map((folder) => ({
        label: folder.name,
        description: folder.uri.fsPath,
        folder: { name: folder.name, fsPath: folder.uri.fsPath }
      })),
      { placeHolder }
    );
    return selected?.folder;
  }

  private activeWorkspaceFolder(): WorkspaceFolderInfo | undefined {
    const editor = vscode.window.activeTextEditor;
    if (editor === undefined) {
      return undefined;
    }

    const folder = vscode.workspace.getWorkspaceFolder(editor.document.uri);
    return folder === undefined ? undefined : { name: folder.name, fsPath: folder.uri.fsPath };
  }
}
