import * as vscode from "vscode";
import { basename, relative } from "node:path";

import { DebouncedFileChangeQueue } from "./debounced-file-change-queue";
import { isAutomaticGuardianRelevantPath } from "./file-relevance";
import { AutomaticGuardianFindingState } from "./automatic-guardian-state";

import type { AutomaticGuardianRunner } from "./automatic-guardian-runner";
import type { GuardianFindingSink } from "./vscode-guardian-controller";
import type { GuardianFileContent } from "./file-content-context";
import type { GuardianFinding, GuardianSeverity } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

const AUTOMATIC_GUARDIAN_DEBOUNCE_MS = 750;

export class VsCodeAutomaticGuardian implements vscode.Disposable {
  private readonly runner: AutomaticGuardianRunner;
  private readonly findingSink: GuardianFindingSink;
  private readonly diagnosticCollection: vscode.DiagnosticCollection;
  private readonly disposables: vscode.Disposable[] = [];
  private readonly watcherDisposables: vscode.Disposable[] = [];
  private readonly findingState = new AutomaticGuardianFindingState();
  private readonly changeQueue: DebouncedFileChangeQueue<vscode.Uri>;

  public constructor(
    runner: AutomaticGuardianRunner,
    findingSink: GuardianFindingSink,
    diagnosticCollection: vscode.DiagnosticCollection = vscode.languages.createDiagnosticCollection("waymark-guardian")
  ) {
    this.runner = runner;
    this.findingSink = findingSink;
    this.diagnosticCollection = diagnosticCollection;
    this.changeQueue = new DebouncedFileChangeQueue<vscode.Uri>({
      delayMs: AUTOMATIC_GUARDIAN_DEBOUNCE_MS,
      setTimer: (callback, delayMs) => setTimeout(callback, delayMs),
      clearTimer: (handle) => {
        clearTimeout(handle as NodeJS.Timeout);
      },
      onFlush: (uris) => {
        void this.checkUris(uris);
      },
      keyFor: (uri) => uri.toString()
    });
  }

  public start(): void {
    this.registerWorkspaceWatchers();
    this.disposables.push(
      vscode.workspace.onDidChangeTextDocument((event) => {
        this.enqueueUri(event.document.uri);
      }),
      vscode.workspace.onDidSaveTextDocument((document) => {
        this.enqueueUri(document.uri);
      }),
      vscode.workspace.onDidCloseTextDocument((document) => {
        this.enqueueUri(document.uri);
      }),
      vscode.workspace.onDidChangeWorkspaceFolders(() => {
        this.disposeWatchers();
        this.registerWorkspaceWatchers();
      }),
      this.diagnosticCollection
    );
  }

  public dispose(): void {
    this.changeQueue.dispose();
    this.disposeWatchers();
    for (const disposable of this.disposables.splice(0)) {
      disposable.dispose();
    }
    this.findingState.clearAll();
  }

  public async recheckUri(uri: vscode.Uri): Promise<void> {
    await this.checkUris([uri]);
  }

  private registerWorkspaceWatchers(): void {
    for (const folder of vscode.workspace.workspaceFolders ?? []) {
      const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(folder, "**/*"));
      this.watcherDisposables.push(
        watcher,
        watcher.onDidCreate((uri) => {
          this.enqueueUri(uri);
        }),
        watcher.onDidChange((uri) => {
          this.enqueueUri(uri);
        }),
        watcher.onDidDelete((uri) => {
          this.clearUri(uri);
        })
      );
    }
  }

  private disposeWatchers(): void {
    for (const disposable of this.watcherDisposables.splice(0)) {
      disposable.dispose();
    }
  }

  private enqueueUri(uri: vscode.Uri): void {
    if (uri.scheme !== "file") {
      return;
    }

    const file = this.fileInfoForUri(uri);
    if (file === undefined || !isAutomaticGuardianRelevantPath(file.relativePath)) {
      return;
    }

    this.changeQueue.enqueue(uri);
  }

  private clearUri(uri: vscode.Uri): void {
    this.diagnosticCollection.delete(uri);
    this.findingState.clearFile(normalizeFilePath(uri.fsPath));
    this.publishAllFindings();
  }

  private async checkUris(uris: readonly vscode.Uri[]): Promise<void> {
    const checks = uris.map((uri) => ({
      uri,
      fileKey: normalizeFilePath(uri.fsPath),
      generation: this.findingState.beginFileCheck(normalizeFilePath(uri.fsPath))
    }));

    const files = await Promise.all(checks.map((check) => this.readGuardianFileContent(check.uri)));
    const readableFiles = files.filter((file): file is GuardianFileContent => file !== undefined);
    if (readableFiles.length === 0) {
      for (const check of checks) {
        const update = this.findingState.applyFileResult({
          fileKey: check.fileKey,
          generation: check.generation,
          findings: []
        });
        if (update.applied) {
          this.diagnosticCollection.delete(check.uri);
        }
      }
      this.publishAllFindings();
      return;
    }

    const result = await this.runner.checkFiles(readableFiles);
    for (const check of checks) {
      const file = readableFiles.find((readableFile) => normalizeFilePath(readableFile.filePath) === check.fileKey);
      const fileFindings = result.fileResults
        .find((fileResult) => normalizeFilePath(fileResult.file.filePath) === check.fileKey)
        ?.result.findings ?? [];
      const update = this.findingState.applyFileResult({
        fileKey: check.fileKey,
        generation: check.generation,
        findings: fileFindings
      });
      if (!update.applied) {
        continue;
      }

      if (file === undefined) {
        this.diagnosticCollection.delete(check.uri);
      } else {
        this.publishDiagnosticsForFile(file, fileFindings);
      }
    }

    this.publishAllFindings();
  }

  private async readGuardianFileContent(uri: vscode.Uri): Promise<GuardianFileContent | undefined> {
    const file = this.fileInfoForUri(uri);
    if (file === undefined || !isAutomaticGuardianRelevantPath(file.relativePath)) {
      return undefined;
    }

    const openDocument = vscode.workspace.textDocuments.find((document) => document.uri.toString() === uri.toString());
    const content = openDocument?.getText() ?? await this.readFile(uri);
    if (content === undefined) {
      return undefined;
    }

    return {
      workspaceFolder: file.workspaceFolder,
      filePath: uri.fsPath,
      relativePath: file.relativePath,
      content
    };
  }

  private async readFile(uri: vscode.Uri): Promise<string | undefined> {
    try {
      const bytes = await vscode.workspace.fs.readFile(uri);
      return Buffer.from(bytes).toString("utf8");
    } catch {
      return undefined;
    }
  }

  private fileInfoForUri(uri: vscode.Uri): { readonly workspaceFolder: WorkspaceFolderInfo; readonly relativePath: string } | undefined {
    const folder = vscode.workspace.getWorkspaceFolder(uri);
    if (folder === undefined) {
      return undefined;
    }

    return {
      workspaceFolder: {
        name: folder.name,
        fsPath: folder.uri.fsPath
      },
      relativePath: relative(folder.uri.fsPath, uri.fsPath)
    };
  }

  private publishDiagnosticsForFile(file: GuardianFileContent, findings: readonly GuardianFinding[]): void {
    const uri = vscode.Uri.file(file.filePath);
    if (findings.length === 0) {
      this.diagnosticCollection.delete(uri);
      return;
    }

    const diagnostics = findings.map((finding) => guardianDiagnosticForFinding(finding, file.content));
    this.diagnosticCollection.set(uri, diagnostics);
  }

  private publishAllFindings(): void {
    this.findingSink.setGuardianFindings(this.findingState.allFindings());
  }
}

function guardianDiagnosticForFinding(finding: GuardianFinding, content: string): vscode.Diagnostic {
  const range = rangeForFinding(content, finding);
  const diagnostic = new vscode.Diagnostic(
    range,
    `${finding.message} Rule: ${finding.ruleTitle}.`,
    severityForFinding(finding.severity)
  );
  diagnostic.source = "Waymark Guardian";
  diagnostic.code = finding.ruleId;
  return diagnostic;
}

function rangeForFinding(content: string, finding: GuardianFinding): vscode.Range {
  for (const term of finding.matchedTerms) {
    const location = findTermLocation(content, term);
    if (location !== undefined) {
      return new vscode.Range(location.line, location.character, location.line, location.character + term.length);
    }
  }

  return new vscode.Range(0, 0, 0, Math.max(1, basename(finding.codePath ?? finding.rulePath).length));
}

function findTermLocation(content: string, term: string): { readonly line: number; readonly character: number } | undefined {
  const lines = content.split(/\r?\n/);
  const normalizedTerm = term.toLowerCase();
  for (let line = 0; line < lines.length; line += 1) {
    const character = lines[line]?.toLowerCase().indexOf(normalizedTerm) ?? -1;
    if (character >= 0) {
      return { line, character };
    }
  }
  return undefined;
}

function severityForFinding(severity: GuardianSeverity): vscode.DiagnosticSeverity {
  return severity === "WARNING" ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Information;
}

function normalizeFilePath(filePath: string): string {
  return filePath.replace(/\\/g, "/").toLowerCase();
}
