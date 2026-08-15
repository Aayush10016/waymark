import * as vscode from "vscode";

import { NvidiaAiProvider, configurationFromEnvironment, diagnosticsForConfiguration } from "./ai/nvidia-provider";
import { GroundedPromptBuilder } from "./ai/prompt-builder";
import { GroundedRagContextBuilder } from "./ai/rag-context-builder";
import { RemediationPromptBuilder } from "./ai/remediation-prompt-builder";
import { VsCodeAiController } from "./ai/vscode-ai-controller";
import { VsCodeRemediationController } from "./ai/vscode-remediation-controller";
import { CodeContextBuilder } from "./code-context/code-context-builder";
import { CodeContextPackageBuilder } from "./code-context/code-context-package-builder";
import { formatCodeContextPackage } from "./code-context/code-context-presenter";
import { VsCodeCodeContextController } from "./code-context/vscode-code-context";
import { GitCliClient } from "./git/git-client";
import { GitRevisionContentProvider } from "./git/git-diff-provider";
import { ProjectContextService } from "./git/project-context";
import { DeterministicGuardianChecker } from "./guardian/guardian-checker";
import { GuardianContextBuilder } from "./guardian/guardian-context-builder";
import { AutomaticGuardianRunner } from "./guardian/automatic-guardian-runner";
import { VsCodeAutomaticGuardian } from "./guardian/vscode-automatic-guardian";
import { VsCodeGuardianController } from "./guardian/vscode-guardian-controller";
import { ProjectMemoryCollector } from "./memory/memory-collector";
import { ProjectMemoryRepository } from "./memory/memory-repository";
import { DeterministicMemoryRetriever } from "./memory/memory-retriever";
import { ConstitutionStore } from "./project-source/constitution";
import { DecisionStore } from "./project-source/decisions";
import { RuleStore } from "./project-source/rules";
import { ProjectMemoryTreeDataProvider } from "./views/project-memory-tree";
import { ProjectMarkerStore } from "./workspace/project-marker";
import { WaymarkWorkspaceService } from "./workspace/workspace-state";

export function activate(context: vscode.ExtensionContext): void {
  const markerStore = new ProjectMarkerStore();
  const workspaceService = new WaymarkWorkspaceService(markerStore);
  const constitutionStore = new ConstitutionStore();
  const ruleStore = new RuleStore();
  const decisionStore = new DecisionStore();
  const gitClient = new GitCliClient();
  const projectContextService = new ProjectContextService(gitClient);
  const gitRevisionContentProvider = new GitRevisionContentProvider(gitClient);
  const memoryRepository = new ProjectMemoryRepository(
    new ProjectMemoryCollector(gitClient),
    new DeterministicMemoryRetriever()
  );
  const codeContextOutputChannel = vscode.window.createOutputChannel("Waymark Code Context");
  const guardianOutputChannel = vscode.window.createOutputChannel("Waymark Guardian");
  const aiOutputChannel = vscode.window.createOutputChannel("Waymark AI");
  const nvidiaConfiguration = configurationFromEnvironment();
  const codeContextPackageBuilder = new CodeContextPackageBuilder(new CodeContextBuilder(), memoryRepository, gitClient);
  const guardianContextBuilder = new GuardianContextBuilder(ruleStore, decisionStore, memoryRepository, gitClient);
  const codeContextController = new VsCodeCodeContextController(
    codeContextPackageBuilder,
    codeContextOutputChannel
  );
  const projectMemoryView = new ProjectMemoryTreeDataProvider(
    workspaceService,
    constitutionStore,
    ruleStore,
    decisionStore,
    projectContextService,
    gitClient,
    gitRevisionContentProvider,
    memoryRepository,
    () => diagnosticsForConfiguration(nvidiaConfiguration)
  );
  const automaticGuardian = new VsCodeAutomaticGuardian(
    new AutomaticGuardianRunner(ruleStore, decisionStore, memoryRepository),
    projectMemoryView
  );
  automaticGuardian.start();
  const remediationController = new VsCodeRemediationController(
    codeContextPackageBuilder,
    new GroundedRagContextBuilder(memoryRepository),
    new RemediationPromptBuilder(),
    new NvidiaAiProvider(nvidiaConfiguration),
    aiOutputChannel,
    nvidiaConfiguration,
    automaticGuardian
  );
  const guardianController = new VsCodeGuardianController(
    codeContextPackageBuilder,
    guardianContextBuilder,
    new DeterministicGuardianChecker(),
    guardianOutputChannel,
    projectMemoryView,
    remediationController
  );
  const aiController = new VsCodeAiController(
    codeContextPackageBuilder,
    guardianContextBuilder,
    new GroundedRagContextBuilder(memoryRepository),
    new GroundedPromptBuilder(),
    new NvidiaAiProvider(nvidiaConfiguration),
    aiOutputChannel,
    nvidiaConfiguration
  );

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider("waymark.projectMemory", projectMemoryView),
    vscode.commands.registerCommand("waymark.openProjectMemory", async () => {
      await vscode.commands.executeCommand("workbench.view.extension.waymark");
    }),
    vscode.commands.registerCommand("waymark.initializeProject", async () => {
      await projectMemoryView.initializeProject();
    }),
    vscode.commands.registerCommand("waymark.selectWorkspaceFolder", (folderPath: string) => {
      projectMemoryView.selectWorkspaceFolder(folderPath);
    }),
    vscode.commands.registerCommand("waymark.createConstitution", async () => {
      await projectMemoryView.createConstitution();
    }),
    vscode.commands.registerCommand("waymark.openConstitution", async () => {
      await projectMemoryView.openConstitution();
    }),
    vscode.commands.registerCommand("waymark.addRule", async () => {
      await projectMemoryView.createRule();
    }),
    vscode.commands.registerCommand("waymark.deleteRule", async (item) => {
      await projectMemoryView.deleteRuleFromTreeItem(item as Parameters<typeof projectMemoryView.deleteRuleFromTreeItem>[0]);
    }),
    vscode.commands.registerCommand("waymark.addDecision", async () => {
      await projectMemoryView.createDecision();
    }),
    vscode.commands.registerCommand("waymark.importArchitectureDocument", async () => {
      await projectMemoryView.importArchitectureDocument();
    }),
    vscode.commands.registerCommand("waymark.showFutureFeature", async (featureName: "Project Memory" | "Guardian") => {
      await projectMemoryView.showFutureFeature(featureName);
    }),
    vscode.commands.registerCommand("waymark.refreshGitContext", () => {
      projectMemoryView.refresh();
    }),
    vscode.commands.registerCommand("waymark.openChangedFile", async (folder, fileStatus) => {
      await projectMemoryView.openChangedFile(folder as Parameters<typeof projectMemoryView.openChangedFile>[0], fileStatus as Parameters<typeof projectMemoryView.openChangedFile>[1]);
    }),
    vscode.commands.registerCommand("waymark.viewFileDiff", async (folder, fileStatus) => {
      await projectMemoryView.viewFileDiff(folder as Parameters<typeof projectMemoryView.viewFileDiff>[0], fileStatus as Parameters<typeof projectMemoryView.viewFileDiff>[1]);
    }),
    vscode.commands.registerCommand("waymark.showCommitDetails", async (folder, sha) => {
      await projectMemoryView.showCommitDetails(folder as Parameters<typeof projectMemoryView.showCommitDetails>[0], sha as string);
    }),
    vscode.commands.registerCommand("waymark.searchProjectMemory", async () => {
      await projectMemoryView.searchProjectMemory();
    }),
    vscode.commands.registerCommand("waymark.refreshProjectMemory", async () => {
      await projectMemoryView.refreshProjectMemory();
    }),
    vscode.commands.registerCommand("waymark.showMemoryResultDetails", async (result) => {
      await projectMemoryView.showMemoryResultDetails(result as Parameters<typeof projectMemoryView.showMemoryResultDetails>[0]);
    }),
    vscode.commands.registerCommand("waymark.explainSelectedCode", async () => {
      await runCodeContextCommand(() => aiController.explainSelectedCode());
    }),
    vscode.commands.registerCommand("waymark.showCodeContext", async () => {
      await runCodeContextCommand(() => codeContextController.showCodeContext(formatCodeContextPackage));
    }),
    vscode.commands.registerCommand("waymark.checkSelectedCode", async () => {
      await runCodeContextCommand(() => guardianController.checkSelectedCode());
    }),
    vscode.commands.registerCommand("waymark.checkCurrentChanges", async () => {
      await runCodeContextCommand(() => guardianController.checkCurrentChanges());
    }),
    vscode.commands.registerCommand("waymark.showGuardianFinding", async (finding) => {
      await runCodeContextCommand(() => guardianController.showFindingDetail(finding as Parameters<typeof guardianController.showFindingDetail>[0]));
    }),
    vscode.commands.registerCommand("waymark.askAboutSelectedCode", async () => {
      await runCodeContextCommand(() => aiController.askAboutSelectedCode());
    }),
    vscode.commands.registerCommand("waymark.showAiConfiguration", async () => {
      await runCodeContextCommand(() => aiController.showConfiguration());
    }),
    codeContextOutputChannel,
    guardianOutputChannel,
    automaticGuardian,
    aiOutputChannel,
    vscode.workspace.registerTextDocumentContentProvider("waymark-git", gitRevisionContentProvider),
    vscode.workspace.onDidChangeWorkspaceFolders(() => {
      projectMemoryView.refresh();
    })
  );

  for (const folder of vscode.workspace.workspaceFolders ?? []) {
    const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(folder, ".trail/**"));
    context.subscriptions.push(
      watcher,
      watcher.onDidCreate(() => {
        projectMemoryView.refresh();
      }),
      watcher.onDidChange(() => {
        projectMemoryView.refresh();
      }),
      watcher.onDidDelete(() => {
        projectMemoryView.refresh();
      })
    );
  }
}

export function deactivate(): void {
  return;
}

async function runCodeContextCommand(command: () => Promise<void>): Promise<void> {
  try {
    await command();
  } catch (error) {
    const message = error instanceof Error ? error.message : "Waymark could not prepare code context.";
    await vscode.window.showErrorMessage(message);
  }
}
