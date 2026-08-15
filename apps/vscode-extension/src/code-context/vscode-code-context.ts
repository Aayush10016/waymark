import * as vscode from "vscode";
import { basename, relative } from "node:path";

import { UserFacingError } from "../shared/errors";

import type { CodeContextPackage } from "./models";
import type { CodeContextPackageBuilder } from "./code-context-package-builder";
import type { SourceSelection, SourceSymbol } from "./models";

export class VsCodeCodeContextController {
  private readonly packageBuilder: CodeContextPackageBuilder;
  private readonly outputChannel: vscode.OutputChannel;
  private lastPackage: CodeContextPackage | undefined;

  public constructor(packageBuilder: CodeContextPackageBuilder, outputChannel: vscode.OutputChannel) {
    this.packageBuilder = packageBuilder;
    this.outputChannel = outputChannel;
  }

  public async explainSelectedCode(formatter: (contextPackage: CodeContextPackage) => string): Promise<void> {
    const contextPackage = await this.buildFromActiveSelection();
    this.lastPackage = contextPackage;
    this.showPackage(contextPackage, formatter);
    await vscode.window.showInformationMessage("Local code context prepared. No AI was used.");
  }

  public async showCodeContext(formatter: (contextPackage: CodeContextPackage) => string): Promise<void> {
    const contextPackage = this.lastPackage ?? (await this.buildFromActiveSelection());
    this.lastPackage = contextPackage;
    this.showPackage(contextPackage, formatter);
  }

  private showPackage(contextPackage: CodeContextPackage, formatter: (contextPackage: CodeContextPackage) => string): void {
    this.outputChannel.clear();
    this.outputChannel.appendLine(formatter(contextPackage));
    this.outputChannel.show(true);
  }

  private async buildFromActiveSelection(): Promise<CodeContextPackage> {
    const selection = await activeSourceSelection();
    return this.packageBuilder.build(selection);
  }
}

export async function activeSourceSelection(): Promise<SourceSelection> {
  const editor = vscode.window.activeTextEditor;
  if (editor === undefined) {
    throw new UserFacingError("No source file is active. Open a source file and select code first.");
  }

  if (editor.selection.isEmpty) {
    throw new UserFacingError("Select some code first.");
  }

  const workspaceFolder = vscode.workspace.getWorkspaceFolder(editor.document.uri);
  if (workspaceFolder === undefined) {
    throw new UserFacingError("Selected file is not inside an open workspace folder.");
  }

  const selectedText = editor.document.getText(editor.selection);
  if (selectedText.trim().length === 0) {
    throw new UserFacingError("Select some code first.");
  }

  const symbols = await documentSymbols(editor.document.uri);
  const workspaceFolderInfo = {
    name: workspaceFolder.name,
    fsPath: workspaceFolder.uri.fsPath
  };

  return {
    workspaceFolder: workspaceFolderInfo,
    filePath: editor.document.uri.fsPath,
    relativePath: relative(workspaceFolder.uri.fsPath, editor.document.uri.fsPath),
    fileName: basename(editor.document.uri.fsPath),
    language: editor.document.languageId,
    fullText: editor.document.getText(),
    selectedText,
    startLine: editor.selection.start.line + 1,
    endLine: editor.selection.end.line + 1,
    selectionStartOffset: editor.document.offsetAt(editor.selection.start),
    selectionEndOffset: editor.document.offsetAt(editor.selection.end),
    symbols
  };
}

async function documentSymbols(uri: vscode.Uri): Promise<readonly SourceSymbol[]> {
  const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[]>(
    "vscode.executeDocumentSymbolProvider",
    uri
  );

  return (symbols ?? []).map(toSourceSymbol);
}

function toSourceSymbol(symbol: vscode.DocumentSymbol): SourceSymbol {
  return {
    name: symbol.name,
    kind: vscode.SymbolKind[symbol.kind] ?? "Unknown",
    startLine: symbol.range.start.line + 1,
    endLine: symbol.range.end.line + 1,
    children: symbol.children.map(toSourceSymbol)
  };
}
