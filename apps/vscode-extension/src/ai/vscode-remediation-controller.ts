import * as vscode from "vscode";
import { basename, relative } from "node:path";

import { contentHash, validateRemediationEdits } from "./remediation-edits";
import { formatRemediationModalSummary, formatRemediationProposal } from "./remediation-presenter";
import { buildRemediationResponse } from "./remediation-response-builder";
import { AiProviderError } from "./provider";

import type { CodeContextPackageBuilder } from "../code-context/code-context-package-builder";
import type { SourceSelection } from "../code-context/models";
import type { GuardianFinding } from "../guardian/models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";
import type { NvidiaProviderConfiguration } from "./nvidia-provider";
import type { AiProvider } from "./provider";
import type { RemediationPromptBuilder } from "./remediation-prompt-builder";
import type { GroundedRagContextBuilder } from "./rag-context-builder";
import type { RemediationProposal } from "./remediation-models";

export interface GuardianRecheckRunner {
  recheckUri(uri: vscode.Uri): Promise<void>;
}

export class VsCodeRemediationController {
  private readonly codeContextPackageBuilder: CodeContextPackageBuilder;
  private readonly ragContextBuilder: GroundedRagContextBuilder;
  private readonly promptBuilder: RemediationPromptBuilder;
  private readonly provider: AiProvider;
  private readonly outputChannel: vscode.OutputChannel;
  private readonly configuration: NvidiaProviderConfiguration;
  private readonly recheckRunner: GuardianRecheckRunner;
  private activeRequest: AbortController | undefined;

  public constructor(
    codeContextPackageBuilder: CodeContextPackageBuilder,
    ragContextBuilder: GroundedRagContextBuilder,
    promptBuilder: RemediationPromptBuilder,
    provider: AiProvider,
    outputChannel: vscode.OutputChannel,
    configuration: NvidiaProviderConfiguration,
    recheckRunner: GuardianRecheckRunner
  ) {
    this.codeContextPackageBuilder = codeContextPackageBuilder;
    this.ragContextBuilder = ragContextBuilder;
    this.promptBuilder = promptBuilder;
    this.provider = provider;
    this.outputChannel = outputChannel;
    this.configuration = configuration;
    this.recheckRunner = recheckRunner;
  }

  public async suggestFix(finding: GuardianFinding): Promise<void> {
    if (finding.codePath === undefined) {
      await vscode.window.showInformationMessage("Waymark can suggest remediation only for findings tied to a source file.");
      return;
    }
    if (this.activeRequest !== undefined) {
      await vscode.window.showInformationMessage("Waymark AI is already processing a remediation request.");
      return;
    }

    const document = await vscode.workspace.openTextDocument(vscode.Uri.file(finding.codePath));
    const snapshotContent = document.getText();
    const sourceSelection = sourceSelectionForFinding(document, snapshotContent, finding);
    const contextPackage = await this.codeContextPackageBuilder.build(sourceSelection);
    const question = [
      "Suggest a safe targeted remediation for this deterministic Guardian finding.",
      `Rule: ${finding.ruleTitle}`,
      `Finding: ${finding.message}`,
      `Matched terms: ${finding.matchedTerms.join(", ")}`
    ].join("\n");
    const ragContext = await this.ragContextBuilder.build(question, contextPackage);
    const prompt = this.promptBuilder.build(contextPackage, ragContext, finding);

    const abortController = new AbortController();
    this.activeRequest = abortController;
    try {
      const providerResult = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: "Waymark AI is preparing a guarded remediation proposal.",
          cancellable: true
        },
        async (_progress, token) => {
          token.onCancellationRequested(() => {
            abortController.abort();
          });
          return this.provider.generateText({
            systemPrompt: prompt.system,
            userPrompt: prompt.user,
            signal: abortController.signal
          });
        }
      );
      const response = buildRemediationResponse(providerResult, prompt);
      const proposal: RemediationProposal = {
        finding,
        response,
        sourceContentHashes: new Map([[finding.codePath, contentHash(snapshotContent)]]),
        provider: providerResult.provider,
        model: providerResult.model
      };
      await this.reviewProposal(proposal);
    } catch (error) {
      await vscode.window.showInformationMessage(toSafeRemediationMessage(error, this.configuration));
    } finally {
      this.activeRequest = undefined;
    }
  }

  private async reviewProposal(proposal: RemediationProposal): Promise<void> {
    this.outputChannel.clear();
    this.outputChannel.appendLine(formatRemediationProposal(proposal));
    this.outputChannel.show(true);

    const actions = proposal.response.changes.length === 0
      ? ["Reject", "Open Rule", "Open File"]
      : ["Apply Fix", "Reject", "Open Rule", "Open File"];
    const action = await vscode.window.showInformationMessage(
      formatRemediationModalSummary(proposal.finding, proposal.response),
      { modal: true },
      ...actions
    );

    switch (action) {
      case "Apply Fix":
        await this.applyProposal(proposal);
        return;
      case "Open Rule":
        await vscode.window.showTextDocument(vscode.Uri.file(proposal.finding.rulePath));
        return;
      case "Open File":
        if (proposal.finding.codePath !== undefined) {
          await vscode.window.showTextDocument(vscode.Uri.file(proposal.finding.codePath));
        }
        return;
      case "Reject":
      case undefined:
        return;
    }
  }

  private async applyProposal(proposal: RemediationProposal): Promise<void> {
    if (proposal.finding.codePath === undefined) {
      await vscode.window.showInformationMessage("Waymark cannot apply a remediation without a source file.");
      return;
    }

    const currentContents = await currentContentsByFilePath(proposal.response.changes);
    const validation = validateRemediationEdits({
      intendedFilePath: proposal.finding.codePath,
      changes: proposal.response.changes,
      currentContentsByFilePath: currentContents,
      expectedContentHashes: proposal.sourceContentHashes
    });
    if (!validation.ok) {
      await vscode.window.showWarningMessage(validation.failure.message);
      return;
    }

    const edit = new vscode.WorkspaceEdit();
    for (const { change } of validation.changes) {
      const document = await vscode.workspace.openTextDocument(vscode.Uri.file(change.filePath));
      const line = document.lineAt(change.endLine - 1);
      edit.replace(
        document.uri,
        new vscode.Range(change.startLine - 1, 0, change.endLine - 1, line.text.length),
        change.replacementText
      );
    }

    const applied = await vscode.workspace.applyEdit(edit);
    if (!applied) {
      await vscode.window.showWarningMessage("Waymark could not apply the remediation edit.");
      return;
    }

    for (const filePath of new Set(proposal.response.changes.map((change) => change.filePath))) {
      await this.recheckRunner.recheckUri(vscode.Uri.file(filePath));
    }

    await vscode.window.showInformationMessage("Waymark applied the remediation proposal and rechecked the affected file.");
  }
}

function sourceSelectionForFinding(document: vscode.TextDocument, content: string, finding: GuardianFinding): SourceSelection {
  const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
  if (workspaceFolder === undefined) {
    throw new Error("Finding file is not inside an open workspace folder.");
  }

  const selectedRange = selectedRangeForFinding(content, finding);
  const selectionText = lineRangeText(content, selectedRange.startLine, selectedRange.endLine);
  const workspaceFolderInfo: WorkspaceFolderInfo = {
    name: workspaceFolder.name,
    fsPath: workspaceFolder.uri.fsPath
  };

  return {
    workspaceFolder: workspaceFolderInfo,
    filePath: document.uri.fsPath,
    relativePath: relative(workspaceFolder.uri.fsPath, document.uri.fsPath),
    fileName: basename(document.uri.fsPath),
    language: document.languageId,
    fullText: content,
    selectedText: selectionText,
    startLine: selectedRange.startLine,
    endLine: selectedRange.endLine,
    selectionStartOffset: offsetAtLine(content, selectedRange.startLine),
    selectionEndOffset: offsetAtLineEnd(content, selectedRange.endLine),
    symbols: []
  };
}

function selectedRangeForFinding(content: string, finding: GuardianFinding): { readonly startLine: number; readonly endLine: number } {
  if (finding.codeStartLine !== undefined && finding.codeEndLine !== undefined) {
    return {
      startLine: finding.codeStartLine,
      endLine: finding.codeEndLine
    };
  }

  const lines = content.split(/\r?\n/);
  const terms = finding.matchedTerms.map((term) => term.toLowerCase());
  const lineIndex = lines.findIndex((line) => terms.some((term) => line.toLowerCase().includes(term)));
  const line = lineIndex >= 0 ? lineIndex + 1 : 1;
  return {
    startLine: line,
    endLine: line
  };
}

async function currentContentsByFilePath(changes: readonly { readonly filePath: string }[]): Promise<ReadonlyMap<string, string>> {
  const contents = new Map<string, string>();
  for (const filePath of new Set(changes.map((change) => change.filePath))) {
    const document = await vscode.workspace.openTextDocument(vscode.Uri.file(filePath));
    contents.set(filePath, document.getText());
  }
  return contents;
}

function lineRangeText(content: string, startLine: number, endLine: number): string {
  return content.split(/\r?\n/).slice(startLine - 1, endLine).join("\n");
}

function offsetAtLine(content: string, line: number): number {
  return content.split(/\r?\n/).slice(0, line - 1).join("\n").length + (line === 1 ? 0 : 1);
}

function offsetAtLineEnd(content: string, line: number): number {
  return offsetAtLine(content, line) + lineRangeText(content, line, line).length;
}

function toSafeRemediationMessage(error: unknown, configuration: NvidiaProviderConfiguration): string {
  void configuration;
  if (!(error instanceof AiProviderError)) {
    return "Waymark AI remediation failed. Deterministic Guardian findings are still available.";
  }

  switch (error.code) {
    case "MISSING_API_KEY":
      return "Waymark AI remediation is not configured. Set NVIDIA_API_KEY in your local environment before launching VS Code.";
    case "MISSING_MODEL":
      return "Waymark AI remediation model is not configured. Set NVIDIA_MODEL in your local environment before launching VS Code.";
    case "RATE_LIMITED":
      return "Waymark AI remediation is temporarily rate limited. Try again shortly.";
    case "NETWORK":
      return "Waymark AI remediation could not reach the NVIDIA service. Guardian remains available.";
    case "TIMEOUT":
      return "Waymark AI remediation timed out. Guardian remains available.";
    case "AUTHENTICATION_FAILED":
    case "FORBIDDEN":
      return "Waymark AI remediation could not authenticate with the configured NVIDIA provider. Check local configuration.";
    case "CANCELLED":
      return "Waymark AI remediation request was cancelled.";
    case "MALFORMED_RESPONSE":
      return "Waymark AI remediation returned an unsupported proposal. No files were modified.";
    case "UNAVAILABLE":
      return "Waymark AI remediation provider is temporarily unavailable. Guardian remains available.";
  }
}
