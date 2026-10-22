import * as vscode from "vscode";

import { activeSourceSelection } from "../code-context/vscode-code-context";
import { DeterministicGuardianChecker } from "../guardian/guardian-checker";
import { formatAiConfigurationDiagnostics, formatGroundedAiResponse, formatGroundedAiResponseHtml } from "./ai-presenter";
import { diagnosticsForConfiguration } from "./nvidia-provider";
import { AiProviderError } from "./provider";
import { buildGroundedAiResponse } from "./response-builder";

import type { CodeContextPackageBuilder } from "../code-context/code-context-package-builder";
import type { GuardianContextBuilder } from "../guardian/guardian-context-builder";
import type { NvidiaProviderConfiguration } from "./nvidia-provider";
import type { AiProvider } from "./provider";
import type { GroundedPromptBuilder } from "./prompt-builder";
import type { GroundedRagContextBuilder } from "./rag-context-builder";

export class VsCodeAiController {
  private readonly codeContextPackageBuilder: CodeContextPackageBuilder;
  private readonly guardianContextBuilder: GuardianContextBuilder;
  private readonly guardianChecker: DeterministicGuardianChecker;
  private readonly ragContextBuilder: GroundedRagContextBuilder;
  private readonly promptBuilder: GroundedPromptBuilder;
  private readonly provider: AiProvider;
  private readonly outputChannel: vscode.OutputChannel;
  private readonly configuration: NvidiaProviderConfiguration;
  private activeRequest: AbortController | undefined;

  public constructor(
    codeContextPackageBuilder: CodeContextPackageBuilder,
    guardianContextBuilder: GuardianContextBuilder,
    ragContextBuilder: GroundedRagContextBuilder,
    promptBuilder: GroundedPromptBuilder,
    provider: AiProvider,
    outputChannel: vscode.OutputChannel,
    configuration: NvidiaProviderConfiguration
  ) {
    this.codeContextPackageBuilder = codeContextPackageBuilder;
    this.guardianContextBuilder = guardianContextBuilder;
    this.guardianChecker = new DeterministicGuardianChecker();
    this.ragContextBuilder = ragContextBuilder;
    this.promptBuilder = promptBuilder;
    this.provider = provider;
    this.outputChannel = outputChannel;
    this.configuration = configuration;
  }

  public async explainSelectedCode(): Promise<void> {
    await this.askAboutSelectedCode([
      "Explain this code.",
      "Structure the answer with:",
      "1. What this code does",
      "2. How it works",
      "3. Important dependencies/context",
      "4. Relevant project rules or decisions, if supplied",
      "5. Potential concerns, only when supported by supplied evidence",
      "6. Evidence used",
      "7. What project evidence is insufficient, if applicable"
    ].join("\n"));
  }

  public async showConfiguration(): Promise<void> {
    await vscode.window.showInformationMessage(formatAiConfigurationDiagnostics(diagnosticsForConfiguration(this.configuration)), { modal: true });
  }

  public async askAboutSelectedCode(defaultQuestion?: string): Promise<void> {
    const question = defaultQuestion ?? await vscode.window.showInputBox({
      prompt: "What would you like to know about this code?",
      placeHolder: "Does this follow our project architecture?"
    });
    if (question === undefined || question.trim().length === 0) {
      return;
    }

    if (this.activeRequest !== undefined) {
      await vscode.window.showInformationMessage("Waymark AI is already processing a request.");
      return;
    }

    const selection = await activeSourceSelection();
    const contextPackage = await this.codeContextPackageBuilder.build(selection);
    const guardianContext = await this.guardianContextBuilder.buildForCodePackage(contextPackage);
    const guardianResult = this.guardianChecker.check(guardianContext);
    const ragContext = await this.ragContextBuilder.build(question, contextPackage);
    const prompt = this.promptBuilder.build(ragContext, guardianResult);

    const abortController = new AbortController();
    this.activeRequest = abortController;
    try {
      const providerResult = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: "Waymark AI is reasoning over selected code and project evidence.",
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
      const response = buildGroundedAiResponse(providerResult, prompt);
      this.outputChannel.clear();
      this.outputChannel.appendLine(formatGroundedAiResponse(response));
      this.outputChannel.show(true);
      const panel = vscode.window.createWebviewPanel("waymark.aiResponse", "Waymark AI", vscode.ViewColumn.Beside, {
        enableScripts: false
      });
      panel.webview.html = formatGroundedAiResponseHtml(response);
    } catch (error) {
      await vscode.window.showInformationMessage(toSafeAiMessage(error));
    } finally {
      this.activeRequest = undefined;
    }
  }
}

function toSafeAiMessage(error: unknown): string {
  if (!(error instanceof AiProviderError)) {
    return "Waymark AI failed. Local Waymark features are still available.";
  }

  switch (error.code) {
    case "MISSING_API_KEY":
      return "Waymark AI is not configured. Set NVIDIA_API_KEY in your local environment before launching VS Code.";
    case "MISSING_MODEL":
      return "Waymark AI model is not configured. Set NVIDIA_MODEL in your local environment before launching VS Code.";
    case "MODEL_UNAVAILABLE":
      return "Waymark AI model or endpoint is unavailable. Check NVIDIA_MODEL and NVIDIA_BASE_URL against NVIDIA Build, then restart VS Code. Local Waymark features are still available.";
    case "RATE_LIMITED":
      return "Waymark AI is temporarily rate limited. Try again shortly.";
    case "NETWORK":
      return "Waymark AI could not reach the NVIDIA service. Local Waymark features are still available.";
    case "TIMEOUT":
      return "Waymark AI request timed out. Local Waymark features are still available.";
    case "AUTHENTICATION_FAILED":
    case "FORBIDDEN":
      return "Waymark AI could not authenticate with the configured NVIDIA provider. Check local configuration.";
    case "CANCELLED":
      return "Waymark AI request was cancelled.";
    case "MALFORMED_RESPONSE":
      return "Waymark AI received an unsupported provider response. Local Waymark features are still available.";
    case "UNAVAILABLE":
      return "Waymark AI provider is temporarily unavailable. Local Waymark features are still available.";
  }
}
