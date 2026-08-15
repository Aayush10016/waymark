import * as vscode from "vscode";
import { join } from "node:path";

import { UserFacingError } from "../shared/errors";
import { extractArchitectureRuleCandidates } from "../project-source/architecture-import";
import { RULE_STATUSES, RULE_TYPES, type RuleStore } from "../project-source/rules";
import { DECISION_STATUSES } from "../project-source/decisions";
import { summarizeGitStatus, type ProjectContextService } from "../git/project-context";

import type { ConstitutionStore } from "../project-source/constitution";
import type { DecisionStore } from "../project-source/decisions";
import type { GitCommitDetail, GitFileStatus, GitRepositoryState, ProjectContext } from "../git/models";
import type { GitClient } from "../git/git-client";
import type { GitRevisionContentProvider } from "../git/git-diff-provider";
import type { ProjectMemoryRepository } from "../memory/memory-repository";
import type { ProjectMemoryDocument, ProjectMemorySearchResult } from "../memory/models";
import type { GuardianFinding } from "../guardian/models";
import type { NvidiaConfigurationDiagnostics } from "../ai/nvidia-provider";

import type { WaymarkWorkspaceService, WaymarkWorkspaceState } from "../workspace/workspace-state";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class ProjectMemoryTreeDataProvider implements vscode.TreeDataProvider<ProjectMemoryTreeItem> {
  private readonly workspaceService: WaymarkWorkspaceService;
  private readonly constitutionStore: ConstitutionStore;
  private readonly ruleStore: RuleStore;
  private readonly decisionStore: DecisionStore;
  private readonly projectContextService: ProjectContextService;
  private readonly gitClient: GitClient;
  private readonly gitRevisionContentProvider: GitRevisionContentProvider;
  private readonly memoryRepository: ProjectMemoryRepository;
  private readonly aiDiagnosticsProvider: () => NvidiaConfigurationDiagnostics;
  private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<ProjectMemoryTreeItem | undefined | null | void>();
  private selectedFolderPath: string | undefined;
  private currentMemoryQuery = "";
  private currentMemoryResults: readonly ProjectMemorySearchResult[] = [];
  private currentGuardianFindings: readonly GuardianFinding[] = [];

  public readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

  public constructor(
    workspaceService: WaymarkWorkspaceService,
    constitutionStore: ConstitutionStore,
    ruleStore: RuleStore,
    decisionStore: DecisionStore,
    projectContextService: ProjectContextService,
    gitClient: GitClient,
    gitRevisionContentProvider: GitRevisionContentProvider,
    memoryRepository: ProjectMemoryRepository,
    aiDiagnosticsProvider: () => NvidiaConfigurationDiagnostics
  ) {
    this.workspaceService = workspaceService;
    this.constitutionStore = constitutionStore;
    this.ruleStore = ruleStore;
    this.decisionStore = decisionStore;
    this.projectContextService = projectContextService;
    this.gitClient = gitClient;
    this.gitRevisionContentProvider = gitRevisionContentProvider;
    this.memoryRepository = memoryRepository;
    this.aiDiagnosticsProvider = aiDiagnosticsProvider;
  }

  public refresh(): void {
    this.memoryRepository.clear();
    this.onDidChangeTreeDataEmitter.fire();
  }

  public selectWorkspaceFolder(folderPath: string): void {
    this.selectedFolderPath = folderPath;
    this.refresh();
  }

  public async initializeProject(): Promise<void> {
    const selectedFolder = await this.resolveWorkspaceFolder("Select a workspace folder to initialize with Waymark");
    if (selectedFolder === undefined) {
      return;
    }

    try {
      await this.workspaceService.initialize(getWorkspaceFolders(), selectedFolder.fsPath);
      this.selectedFolderPath = selectedFolder.fsPath;
      this.refresh();
    } catch (error) {
      const message =
        error instanceof UserFacingError
          ? error.message
          : "Waymark could not initialize this workspace. Check file permissions and try again.";
      await vscode.window.showErrorMessage(message);
    }
  }

  public async createConstitution(): Promise<void> {
    const folder = await this.requireInitializedWorkspaceFolder();
    if (folder === undefined) {
      return;
    }

    try {
      const constitution = await this.constitutionStore.create(folder);
      await openMarkdownFile(constitution.path);
      this.refresh();
    } catch (error) {
      await showSafeError(error, "Waymark could not create the Project Constitution.");
    }
  }

  public async openConstitution(): Promise<void> {
    const folder = await this.requireInitializedWorkspaceFolder();
    if (folder === undefined) {
      return;
    }

    const constitution = await this.constitutionStore.read(folder);
    if (constitution.status === "missing") {
      await vscode.window.showInformationMessage("No Project Constitution exists yet.");
      return;
    }

    await openMarkdownFile(constitution.path);
  }

  public async createRule(): Promise<void> {
    const folder = await this.requireInitializedWorkspaceFolder();
    if (folder === undefined) {
      return;
    }

    const title = await promptRequiredInput("Rule title", "Postgres should be used");
    if (title === undefined) {
      return;
    }

    const selectedType = await vscode.window.showQuickPick(
      RULE_TYPES.map((ruleType) => ({
        label: toDisplayLabel(ruleType),
        value: ruleType
      })),
      {
        placeHolder: "Rule type"
      }
    );
    if (selectedType === undefined) {
      return;
    }

    const selectedScope = await vscode.window.showQuickPick(
      RULE_SCOPES.map((scope) => ({
        label: toDisplayLabel(scope),
        value: scope
      })),
      {
        placeHolder: "Rule scope"
      }
    );
    if (selectedScope === undefined) {
      return;
    }

    const selectedStatus = await vscode.window.showQuickPick(
      RULE_STATUSES.map((status) => ({
        label: toDisplayLabel(status),
        value: status
      })),
      {
        placeHolder: "Rule status"
      }
    );
    if (selectedStatus === undefined) {
      return;
    }

    const ruleText = await promptRequiredInput(
      "Rule statement",
      "Backend persistence must use PostgreSQL. Do not introduce Redis for caching or persistence."
    );
    if (ruleText === undefined) {
      return;
    }

    const reason = await promptRequiredInput("Reason", "Keep infrastructure simple and aligned with the project architecture.");
    if (reason === undefined) {
      return;
    }

    const source = await promptRequiredInput("Source", "Project architecture");
    if (source === undefined) {
      return;
    }

    try {
      const rule = await this.ruleStore.create(folder, title, selectedType.value, {
        scope: selectedScope.value,
        status: selectedStatus.value,
        ruleText,
        reason,
        source
      });
      await openMarkdownFile(rule.path);
      this.refresh();
    } catch (error) {
      await showSafeError(error, "Waymark could not create the rule.");
    }
  }

  public async deleteRule(rule: { readonly id: string; readonly title: string; readonly workspaceFolder?: WorkspaceFolderInfo }): Promise<void> {
    const folder = rule.workspaceFolder ?? await this.resolveWorkspaceFolder("Select a workspace folder");
    if (folder === undefined) {
      return;
    }

    const confirmation = await vscode.window.showWarningMessage(
      `Delete Waymark rule "${rule.title}"? This cannot be undone by Waymark.`,
      { modal: true },
      "Delete Rule"
    );
    if (confirmation !== "Delete Rule") {
      return;
    }

    try {
      await this.ruleStore.delete(folder, rule.id);
      this.refresh();
      await vscode.window.showInformationMessage(`Deleted Waymark rule "${rule.title}".`);
    } catch (error) {
      await showSafeError(error, "Waymark could not delete the rule.");
    }
  }

  public async deleteRuleFromTreeItem(item: ProjectMemoryTreeItem | undefined): Promise<void> {
    if (item?.nodeId?.startsWith("rule:") !== true) {
      return;
    }

    const payload = JSON.parse(item.nodeId.slice("rule:".length)) as {
      readonly id: string;
      readonly title: string;
      readonly workspaceFolder: WorkspaceFolderInfo;
    };
    await this.deleteRule(payload);
  }

  public async createDecision(): Promise<void> {
    const folder = await this.requireInitializedWorkspaceFolder();
    if (folder === undefined) {
      return;
    }

    const title = await vscode.window.showInputBox({
      prompt: "Decision title",
      placeHolder: "Use PostgreSQL"
    });
    if (title === undefined) {
      return;
    }

    const selectedStatus = await vscode.window.showQuickPick(
      DECISION_STATUSES.map((status) => ({
        label: status,
        value: status
      })),
      {
        placeHolder: "Decision status"
      }
    );
    if (selectedStatus === undefined) {
      return;
    }

    const contextText = await vscode.window.showInputBox({
      prompt: "Context",
      placeHolder: "Why this decision was needed"
    });
    if (contextText === undefined) {
      return;
    }

    const decisionText = await vscode.window.showInputBox({
      prompt: "Decision",
      placeHolder: "What was decided"
    });
    if (decisionText === undefined) {
      return;
    }

    const consequences = await vscode.window.showInputBox({
      prompt: "Consequences",
      placeHolder: "What this means for the project"
    });
    if (consequences === undefined) {
      return;
    }

    try {
      const decision = await this.decisionStore.create(folder, title, {
        status: selectedStatus.value,
        context: contextText,
        decision: decisionText,
        consequences
      });
      await openMarkdownFile(decision.path);
      this.refresh();
    } catch (error) {
      await showSafeError(error, "Waymark could not create the decision.");
    }
  }

  public async showFutureFeature(featureName: "Project Memory" | "Guardian"): Promise<void> {
    await vscode.window.showInformationMessage(`${featureName} will be available in a future Waymark milestone.`);
  }

  public async searchProjectMemory(): Promise<void> {
    const query = await vscode.window.showInputBox({
      prompt: "Search project memory",
      placeHolder: "PostgreSQL"
    });
    if (query === undefined) {
      return;
    }

    this.currentMemoryQuery = query;
    this.currentMemoryResults = await this.memoryRepository.search(query, getWorkspaceFolders());
    this.refreshTreeOnly();
    if (this.currentMemoryResults.length === 0) {
      const action = await vscode.window.showInformationMessage(
        `No project memory found for '${query}'.`,
        "Search again",
        "Add Rule"
      );
      if (action === "Search again") {
        await this.searchProjectMemory();
      }
      if (action === "Add Rule") {
        await this.createRule();
      }
      return;
    }

    const selected = await vscode.window.showQuickPick(
      this.currentMemoryResults.map((result) => ({
        label: result.document.title,
        description: `${result.document.sourceType} · ${result.document.workspaceFolder.name}`,
        detail: result.document.path ?? `Matched: ${result.matchedFields.join(", ")}`,
        result
      })),
      { placeHolder: `Project memory results for '${query}'` }
    );
    if (selected !== undefined) {
      await this.openMemorySource(selected.result.document);
    }
  }

  public async refreshProjectMemory(): Promise<void> {
    this.memoryRepository.clear();
    if (this.currentMemoryQuery.trim().length > 0) {
      this.currentMemoryResults = await this.memoryRepository.search(this.currentMemoryQuery, getWorkspaceFolders());
    }
    this.refreshTreeOnly();
  }

  public async showMemoryResultDetails(result: ProjectMemorySearchResult): Promise<void> {
    await this.openMemorySource(result.document);
  }

  public setGuardianFindings(findings: readonly GuardianFinding[]): void {
    this.currentGuardianFindings = findings;
    this.refreshTreeOnly();
  }

  public async openMemorySource(document: ProjectMemoryDocument): Promise<void> {
    switch (document.sourceType) {
      case "PROJECT":
      case "CONSTITUTION":
      case "RULE":
      case "DECISION":
        if (document.path !== undefined) {
          await openMarkdownFile(document.path);
        }
        return;
      case "GIT_COMMIT": {
        const sha = document.metadata.sha;
        if (sha !== undefined) {
          await this.showCommitDetails(document.workspaceFolder, sha);
        }
        return;
      }
      case "GIT_STATUS":
        await this.showFutureGitStatusMessage();
        return;
      case "GIT_FILE":
        if (document.path === undefined) {
          return;
        }
        await this.openChangedFile(document.workspaceFolder, {
          path: document.path,
          previousPath: document.metadata.previousPath === "" ? undefined : document.metadata.previousPath,
          staged: document.metadata.staged === "true",
          unstaged: document.metadata.unstaged === "true",
          untracked: document.metadata.untracked === "true",
          deleted: document.metadata.deleted === "true",
          renamed: document.metadata.renamed === "true"
        });
        return;
    }
  }

  public async importArchitectureDocument(): Promise<void> {
    const folder = await this.requireInitializedWorkspaceFolder();
    if (folder === undefined) {
      return;
    }

    const selectedFiles = await vscode.window.showOpenDialog({
      canSelectFiles: true,
      canSelectFolders: false,
      canSelectMany: false,
      filters: {
        "Markdown or text": ["md", "markdown", "txt"]
      },
      title: "Import Architecture Document"
    });
    const sourceUri = selectedFiles?.[0];
    if (sourceUri === undefined) {
      return;
    }

    const content = Buffer.from(await vscode.workspace.fs.readFile(sourceUri)).toString("utf8");
    const candidates = extractArchitectureRuleCandidates(content);
    const action = await vscode.window.showInformationMessage(
      `Waymark found ${candidates.length} structured rule candidate(s).`,
      { modal: true },
      "Use as Constitution",
      "Create Rules",
      "Cancel"
    );

    if (action === "Use as Constitution") {
      await this.importAsConstitution(folder, content);
      return;
    }

    if (action === "Create Rules") {
      await this.createRulesFromArchitectureDocument(folder, sourceUri.fsPath, candidates);
    }
  }

  public async openChangedFile(workspaceFolder: WorkspaceFolderInfo, fileStatus: GitFileStatus): Promise<void> {
    if (fileStatus.deleted) {
      await vscode.window.showInformationMessage("This file no longer exists in the working tree.");
      return;
    }

    await vscode.window.showTextDocument(vscode.Uri.file(join(workspaceFolder.fsPath, fileStatus.path)));
  }

  public async viewFileDiff(workspaceFolder: WorkspaceFolderInfo, fileStatus: GitFileStatus): Promise<void> {
    const left = this.gitRevisionContentProvider.uri(workspaceFolder, fileStatus.previousPath ?? fileStatus.path);
    const right = fileStatus.deleted
      ? this.gitRevisionContentProvider.uri(workspaceFolder, fileStatus.path, "HEAD")
      : vscode.Uri.file(join(workspaceFolder.fsPath, fileStatus.path));

    await vscode.commands.executeCommand("vscode.diff", left, right, `${fileStatus.path} (Waymark Git Diff)`);
  }

  public async showCommitDetails(workspaceFolder: WorkspaceFolderInfo, sha: string): Promise<void> {
    try {
      const detail = await this.gitClient.getCommitDetails(workspaceFolder, sha);
      const action = await vscode.window.showInformationMessage(formatCommitDetail(detail), { modal: true }, "View Changes");
      if (action !== "View Changes" || detail.changedFiles.length === 0) {
        return;
      }

      const selectedFile = await vscode.window.showQuickPick([...detail.changedFiles], {
        placeHolder: "Select a changed file"
      });
      if (selectedFile === undefined) {
        return;
      }

      await vscode.commands.executeCommand(
        "vscode.diff",
        this.gitRevisionContentProvider.uri(workspaceFolder, selectedFile, `${detail.sha}^`),
        this.gitRevisionContentProvider.uri(workspaceFolder, selectedFile, detail.sha),
        `${selectedFile} (${detail.shortSha})`
      );
    } catch (error) {
      await showSafeError(error, "Waymark could not read this commit.");
    }
  }

  private async importAsConstitution(folder: WorkspaceFolderInfo, content: string): Promise<void> {
    const constitution = await this.constitutionStore.read(folder);
    if (constitution.status === "configured") {
      await vscode.window.showInformationMessage("Project Constitution already exists. Waymark did not overwrite it.");
      await openMarkdownFile(constitution.path);
      return;
    }

    await vscode.workspace.fs.writeFile(vscode.Uri.file(constitution.path), Buffer.from(content, "utf8"));
    this.refresh();
    await openMarkdownFile(constitution.path);
  }

  private async createRulesFromArchitectureDocument(
    folder: WorkspaceFolderInfo,
    sourcePath: string,
    candidates: readonly { readonly title: string; readonly statement: string }[]
  ): Promise<void> {
    if (candidates.length === 0) {
      await vscode.window.showInformationMessage("Waymark found no structured MUST/MUST NOT rule statements to import.");
      return;
    }

    let created = 0;
    for (const candidate of candidates) {
      const action = await vscode.window.showInformationMessage(
        `Create Waymark rule?\n\n${candidate.statement}`,
        { modal: true },
        "Create Rule",
        "Skip",
        "Cancel"
      );
      if (action === "Cancel" || action === undefined) {
        break;
      }
      if (action === "Skip") {
        continue;
      }

      await this.ruleStore.createFromInput(folder, {
        title: candidate.title,
        type: "architecture",
        scope: "project-wide",
        status: "active",
        ruleText: candidate.statement,
        reason: "Imported from an architecture document after explicit user confirmation.",
        source: sourcePath
      });
      created += 1;
    }

    this.refresh();
    await vscode.window.showInformationMessage(`Created ${created} Waymark rule${created === 1 ? "" : "s"} from architecture document.`);
  }

  private refreshTreeOnly(): void {
    this.onDidChangeTreeDataEmitter.fire();
  }

  public getTreeItem(element: ProjectMemoryTreeItem): vscode.TreeItem {
    return element;
  }

  public async getChildren(element?: ProjectMemoryTreeItem): Promise<ProjectMemoryTreeItem[]> {
    const state = await this.workspaceService.getState(getWorkspaceFolders(), this.selectedFolderPath);
    if (element === undefined) {
      return this.rootItems(state);
    }

    return this.childItems(element, state);
  }

  private rootItems(state: WaymarkWorkspaceState): ProjectMemoryTreeItem[] {
    switch (state.kind) {
      case "noWorkspace":
        return [
          textItem("No workspace open."),
          descriptionItem("Open a workspace to initialize a Waymark project.")
        ];
      case "selectWorkspace":
        return [
          sectionItem("PROJECT", "project"),
          sectionItem("CONTEXT", "context"),
          sectionItem("MEMORY", "memory"),
          sectionItem("GUARDIAN", "guardian"),
          sectionItem("AI", "ai")
        ];
      case "malformedProject":
        return [
          sectionItem("PROJECT", "project"),
          textItem("$(warning) Invalid .trail/project.json"),
          descriptionItem(state.reason)
        ];
      case "notInitialized":
      case "initialized":
        return [
          sectionItem("PROJECT", "project"),
          sectionItem("CONTEXT", "context"),
          sectionItem("MEMORY", "memory"),
          sectionItem("GUARDIAN", "guardian"),
          sectionItem("AI", "ai")
        ];
    }
  }

  private async childItems(element: ProjectMemoryTreeItem, state: WaymarkWorkspaceState): Promise<ProjectMemoryTreeItem[]> {
    if (element.children !== undefined) {
      return [...element.children];
    }

    switch (element.nodeId) {
      case "project":
        if (state.kind === "selectWorkspace") {
          return [textItem("Select a workspace folder"), ...state.folders.map((folder) => workspaceFolderItem(folder))];
        }
        break;
      case "context":
        return [branchItem("Git", "git")];
      case "git":
        return this.gitItems();
      case "memory":
        return [branchItem("Project Memory", "project-memory")];
      case "project-memory":
        return this.projectMemoryItems();
      case "guardian":
        return this.guardianItems();
      case "guardian-findings":
        return this.guardianFindingItems();
      case "ai":
        return this.aiItems();
      default:
        if (element.nodeId?.startsWith("git-folder:") === true) {
          return this.gitFolderItems(JSON.parse(element.nodeId.slice("git-folder:".length)) as WorkspaceFolderInfo);
        }
        if (element.nodeId?.startsWith("git-status:") === true) {
          const payload = JSON.parse(element.nodeId.slice("git-status:".length)) as GitStatusNodePayload;
          return this.gitStatusItems(payload);
        }
        if (element.nodeId?.startsWith("git-commits:") === true) {
          const folder = JSON.parse(element.nodeId.slice("git-commits:".length)) as WorkspaceFolderInfo;
          return this.gitCommitItems(folder);
        }
    }

    if (state.kind !== "initialized" && state.kind !== "notInitialized") {
      return [];
    }

    switch (element.nodeId) {
      case "project":
        return state.kind === "initialized" ? this.projectItems(state) : notInitializedItems();
      case "constitution":
        return state.kind === "initialized" ? this.constitutionItems(state.folder) : [];
      case "rules":
        return state.kind === "initialized" ? this.ruleItems(state.folder) : [];
      case "decisions":
        return state.kind === "initialized" ? this.decisionItems(state.folder) : [];
      default:
        return [];
    }
  }

  private projectItems(state: Extract<WaymarkWorkspaceState, { readonly kind: "initialized" }>): ProjectMemoryTreeItem[] {
    return [
      textItem("Overview", `Project: ${state.project.name}`),
      branchItem("Constitution", "constitution"),
      branchItem("Rules", "rules"),
      branchItem("Decisions", "decisions")
    ];
  }

  private async constitutionItems(folder: WorkspaceFolderInfo): Promise<ProjectMemoryTreeItem[]> {
    const constitution = await this.constitutionStore.read(folder);
    if (constitution.status === "missing") {
      return [
        textItem("Project Constitution not created yet."),
        actionItem("Create Constitution", "trail.createConstitution")
      ];
    }

    return [
      textItem("$(pass) Constitution configured"),
      actionItem("Open Constitution", "trail.openConstitution")
    ];
  }

  private async ruleItems(folder: WorkspaceFolderInfo): Promise<ProjectMemoryTreeItem[]> {
    const rules = await this.ruleStore.list(folder);
    const items = rules.map((rule) => {
      const item = new ProjectMemoryTreeItem(
        `● ${rule.title}`,
        `rule:${JSON.stringify({ id: rule.id, title: rule.title, workspaceFolder: folder })}`
      );
      item.description = `${toDisplayLabel(rule.type)} · ${toDisplayLabel(rule.status)}${rule.hasMetadataWarning ? " · metadata warning" : ""}`;
      item.command = {
        command: "vscode.open",
        title: "Open Rule",
        arguments: [vscode.Uri.file(rule.path)]
      };
      item.contextValue = "trailRule";
      item.tooltip = `${rule.title}\n${rule.ruleText}\n${rule.path}`;
      return item;
    });

    return [...items, actionItem("+ Add Rule", "trail.addRule")];
  }

  private async decisionItems(folder: WorkspaceFolderInfo): Promise<ProjectMemoryTreeItem[]> {
    const decisions = await this.decisionStore.list(folder);
    const items = decisions.map((decision) => {
      const item = textItem(decision.title);
      item.description = `${decision.rawStatus ?? decision.status}${decision.hasMetadataWarning ? " · metadata warning" : ""}`;
      item.command = {
        command: "vscode.open",
        title: "Open Decision",
        arguments: [vscode.Uri.file(decision.path)]
      };
      return item;
    });

    return [...items, actionItem("+ Add Decision", "trail.addDecision")];
  }

  private async gitItems(): Promise<ProjectMemoryTreeItem[]> {
    const folders = getWorkspaceFolders();
    if (folders.length === 0) {
      return [textItem("No workspace open.")];
    }

    const contexts = await this.projectContextService.getContexts(folders);
    if (contexts.length === 1) {
      return this.gitStateItems(contexts[0]?.git);
    }

    return contexts.map((context) => gitFolderItem(context));
  }

  private async gitFolderItems(folder: WorkspaceFolderInfo): Promise<ProjectMemoryTreeItem[]> {
    const context = await this.projectContextService.getContext(folder);
    return this.gitStateItems(context.git);
  }

  private gitStateItems(state: GitRepositoryState | undefined): ProjectMemoryTreeItem[] {
    if (state === undefined) {
      return [textItem("Unable to read repository.")];
    }

    switch (state.kind) {
      case "gitUnavailable":
        return [textItem("Git is unavailable.")];
      case "notRepository":
        return [textItem("Not a Git repository.")];
      case "readFailure":
        return [textItem("Unable to read repository."), descriptionItem(state.message)];
      case "repository": {
        const summary = summarizeGitStatus(state);
        return [
          textItem(state.repository.isDetachedHead ? "Detached HEAD" : "Branch", state.repository.branch ?? state.repository.headCommit),
          textItem("Working Tree", `${summary.changedFiles} changed files`),
          textItem("Staged", summary.staged.toString()),
          textItem("Unstaged", summary.unstaged.toString()),
          textItem("Untracked", summary.untracked.toString()),
          actionItem("Refresh", "trail.refreshGitContext"),
          branchItem("Changed Files", `git-status:${JSON.stringify({ workspaceFolder: state.workspaceFolder, status: state.status })}`),
          branchItem("Recent Commits", `git-commits:${JSON.stringify(state.workspaceFolder)}`)
        ];
      }
    }
  }

  private gitStatusItems(payload: GitStatusNodePayload): ProjectMemoryTreeItem[] {
    const categories = [
      statusCategoryItem("Staged", payload.workspaceFolder, payload.status.filter((file) => file.staged)),
      statusCategoryItem("Unstaged", payload.workspaceFolder, payload.status.filter((file) => file.unstaged)),
      statusCategoryItem("Untracked", payload.workspaceFolder, payload.status.filter((file) => file.untracked))
    ];
    return categories.filter((item): item is ProjectMemoryTreeItem => item !== undefined);
  }

  private async gitCommitItems(folder: WorkspaceFolderInfo): Promise<ProjectMemoryTreeItem[]> {
    const commits = await this.gitClient.getRecentCommits(folder);
    return commits.map((commit) => {
      const item = textItem(commit.subject, `${commit.shortSha} · ${commit.author} · ${commit.date}`);
      item.command = {
        command: "trail.showCommitDetails",
        title: "Show Commit Details",
        arguments: [folder, commit.sha]
      };
      return item;
    });
  }

  private async resolveWorkspaceFolder(placeHolder: string): Promise<WorkspaceFolderInfo | undefined> {
    const folders = getWorkspaceFolders();
    if (folders.length === 0) {
      await vscode.window.showErrorMessage("Open a workspace before using Waymark.");
      return undefined;
    }

    if (folders.length === 1) {
      return folders[0];
    }

    const selected = await vscode.window.showQuickPick(
      folders.map((folder) => ({
        label: folder.name,
        description: folder.fsPath,
        folder
      })),
      {
        placeHolder
      }
    );
    return selected?.folder;
  }

  private async requireInitializedWorkspaceFolder(): Promise<WorkspaceFolderInfo | undefined> {
    const selectedFolder = await this.resolveWorkspaceFolder("Select a workspace folder");
    if (selectedFolder === undefined) {
      return undefined;
    }

    const state = await this.workspaceService.getState(getWorkspaceFolders(), selectedFolder.fsPath);
    if (state.kind === "initialized") {
      this.selectedFolderPath = selectedFolder.fsPath;
      return selectedFolder;
    }

    await vscode.window.showInformationMessage("Initialize this workspace with Waymark before adding project truth.");
    return undefined;
  }

  private projectMemoryItems(): ProjectMemoryTreeItem[] {
    if (this.currentMemoryQuery.trim().length === 0) {
      return [
        actionItem("Search project memory...", "trail.searchProjectMemory"),
        actionItem("Import Architecture Document", "trail.importArchitectureDocument"),
        textItem("Sources", "Constitution · Rules · Decisions · Git"),
        descriptionItem("Deterministic lexical retrieval only.")
      ];
    }

    if (this.currentMemoryResults.length === 0) {
      return [
        actionItem("Search project memory...", "trail.searchProjectMemory"),
        actionItem("Import Architecture Document", "trail.importArchitectureDocument"),
        actionItem("Refresh Project Memory", "trail.refreshProjectMemory"),
        textItem(`No project memory results found for: ${this.currentMemoryQuery}`),
        descriptionItem("Try another project term.")
      ];
    }

    return [
      actionItem("Search project memory...", "trail.searchProjectMemory"),
      actionItem("Import Architecture Document", "trail.importArchitectureDocument"),
      actionItem("Refresh Project Memory", "trail.refreshProjectMemory"),
      ...this.currentMemoryResults.map((result) => memoryResultItem(result))
    ];
  }

  private guardianItems(): ProjectMemoryTreeItem[] {
    const findingsLabel = this.currentGuardianFindings.length === 0
      ? "Findings"
      : `$(warning) ${this.currentGuardianFindings.length} potential conflict${this.currentGuardianFindings.length === 1 ? "" : "s"}`;

    return [
      branchItem(findingsLabel, "guardian-findings"),
      actionItem("Check Current Changes", "trail.checkCurrentChanges"),
      actionItem("Check Selected Code", "trail.checkSelectedCode")
    ];
  }

  private guardianFindingItems(): ProjectMemoryTreeItem[] {
    if (this.currentGuardianFindings.length === 0) {
      return [
        textItem("No findings yet."),
        descriptionItem("Run a Guardian check to populate this list.")
      ];
    }

    return this.currentGuardianFindings.map((finding) => guardianFindingItem(finding));
  }

  private aiItems(): ProjectMemoryTreeItem[] {
    const diagnostics = this.aiDiagnosticsProvider();
    const isReady = diagnostics.apiKeyStatus === "Configured" && diagnostics.modelStatus === "Configured";

    return [
      actionItem("Explain Selected Code", "trail.explainSelectedCode"),
      actionItem("Ask About Selected Code", "trail.askAboutSelectedCode"),
      actionItem(`Status: ${isReady ? "Ready" : "Not configured"}`, "trail.showAiConfiguration"),
      descriptionItem(isReady ? `NVIDIA · ${diagnostics.model ?? "(missing model)"}` : "Set NVIDIA_API_KEY and NVIDIA_MODEL, then restart VS Code.")
    ];
  }

  private async showFutureGitStatusMessage(): Promise<void> {
    await vscode.window.showInformationMessage("Git working tree evidence is shown in the Waymark Git context view.");
  }
}

interface GitStatusNodePayload {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly status: readonly GitFileStatus[];
}

export class ProjectMemoryTreeItem extends vscode.TreeItem {
  public constructor(
    label: string,
    public readonly nodeId?: string,
    collapsibleState: vscode.TreeItemCollapsibleState = vscode.TreeItemCollapsibleState.None,
    public readonly children?: readonly ProjectMemoryTreeItem[]
  ) {
    super(label, collapsibleState);
  }
}

function notInitializedItems(): ProjectMemoryTreeItem[] {
  return [
    textItem("Not initialized"),
    descriptionItem("Waymark uses a local .trail directory for constitution, rules, decisions, and memory."),
    descriptionItem("This workspace has not been initialized with Waymark."),
    initializeItem()
  ];
}

function sectionItem(label: string, nodeId: string): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(label, nodeId, vscode.TreeItemCollapsibleState.Expanded);
  item.contextValue = "section";
  return item;
}

function branchItem(label: string, nodeId: string): ProjectMemoryTreeItem {
  return new ProjectMemoryTreeItem(label, nodeId, vscode.TreeItemCollapsibleState.Collapsed);
}

function textItem(label: string, description?: string): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(label);
  if (description !== undefined) {
    item.description = description;
  }
  return item;
}

function descriptionItem(label: string): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(label);
  item.description = true;
  return item;
}

function initializeItem(): ProjectMemoryTreeItem {
  return actionItem("Initialize Project", "trail.initializeProject");
}

function actionItem(label: string, command: string): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(label);
  item.command = {
    command,
    title: label
  };
  return item;
}

function guardianFindingItem(finding: GuardianFinding): ProjectMemoryTreeItem {
  const item = textItem(finding.ruleTitle, finding.matchedTerms.join(", "));
  item.tooltip = `${finding.title}\n${finding.message}\nRule: ${finding.rulePath}`;
  item.command = {
    command: "trail.showGuardianFinding",
    title: "Show Guardian Finding",
    arguments: [finding]
  };
  return item;
}

function memoryResultItem(result: ProjectMemorySearchResult): ProjectMemoryTreeItem {
  const item = textItem(result.document.title, `${result.document.sourceType} · ${result.document.workspaceFolder.name}`);
  item.command = {
    command: "trail.showMemoryResultDetails",
    title: "Show Memory Result Details",
    arguments: [result]
  };
  item.tooltip = `Matched: ${result.matchedFields.join(", ")}\nTerms: ${result.matchedTerms.join(", ")}`;
  return item;
}

function gitFolderItem(context: ProjectContext): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(
    context.workspaceFolder.name,
    `git-folder:${JSON.stringify(context.workspaceFolder)}`,
    vscode.TreeItemCollapsibleState.Collapsed
  );
  item.description = gitFolderDescription(context.git);
  return item;
}

function gitFolderDescription(state: GitRepositoryState): string {
  switch (state.kind) {
    case "repository":
      return `${state.repository.branch ?? state.repository.headCommit} · ${state.status.length} changed files`;
    case "gitUnavailable":
      return "Git unavailable";
    case "notRepository":
      return "Not a Git repository";
    case "readFailure":
      return "Unable to read repository";
  }
}

function statusCategoryItem(
  label: string,
  workspaceFolder: WorkspaceFolderInfo,
  files: readonly GitFileStatus[]
): ProjectMemoryTreeItem | undefined {
  if (files.length === 0) {
    return undefined;
  }

  const item = new ProjectMemoryTreeItem(
    label,
    undefined,
    vscode.TreeItemCollapsibleState.Expanded,
    files.map((file) => gitFileItem(workspaceFolder, file))
  );
  item.description = files.length.toString();
  item.contextValue = "gitStatusCategory";
  return item;
}

function gitFileItem(workspaceFolder: WorkspaceFolderInfo, fileStatus: GitFileStatus): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(
    `${gitFileIcon(fileStatus)} ${fileStatus.path}`,
    undefined,
    vscode.TreeItemCollapsibleState.Collapsed,
    [gitFileActionItem("View Diff", "trail.viewFileDiff", workspaceFolder, fileStatus)]
  );
  if (fileStatus.renamed && fileStatus.previousPath !== undefined) {
    item.description = `renamed from ${fileStatus.previousPath}`;
  }
  item.command = {
    command: "trail.openChangedFile",
    title: "Open Changed File",
    arguments: [workspaceFolder, fileStatus]
  };
  item.contextValue = "gitFile";
  return item;
}

function gitFileActionItem(
  label: string,
  command: string,
  workspaceFolder: WorkspaceFolderInfo,
  fileStatus: GitFileStatus
): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(label);
  item.command = {
    command,
    title: label,
    arguments: [workspaceFolder, fileStatus]
  };
  return item;
}

function gitFileIcon(fileStatus: GitFileStatus): string {
  if (fileStatus.untracked) {
    return "+";
  }
  if (fileStatus.deleted) {
    return "D";
  }
  if (fileStatus.renamed) {
    return "R";
  }
  return "M";
}

function workspaceFolderItem(folder: WorkspaceFolderInfo): ProjectMemoryTreeItem {
  const item = new ProjectMemoryTreeItem(folder.name);
  item.description = folder.fsPath;
  item.command = {
    command: "trail.selectWorkspaceFolder",
    title: "Select Workspace Folder",
    arguments: [folder.fsPath]
  };
  return item;
}

function getWorkspaceFolders(): WorkspaceFolderInfo[] {
  return (vscode.workspace.workspaceFolders ?? []).map((folder) => ({
    name: folder.name,
    fsPath: folder.uri.fsPath
  }));
}

const RULE_SCOPES = [
  "project-wide",
  "backend",
  "frontend",
  "extension",
  "vscode",
  "java",
  "typescript",
  "javascript",
  "python"
] as const;

async function openMarkdownFile(path: string): Promise<void> {
  await vscode.window.showTextDocument(vscode.Uri.file(path));
}

async function promptRequiredInput(prompt: string, placeHolder: string): Promise<string | undefined> {
  const value = await vscode.window.showInputBox({
    prompt,
    placeHolder,
    validateInput: (candidate) => candidate.trim().length === 0 ? `${prompt} is required.` : undefined
  });
  const trimmed = value?.trim();
  return trimmed === undefined || trimmed.length === 0 ? undefined : trimmed;
}

async function showSafeError(error: unknown, fallback: string): Promise<void> {
  await vscode.window.showErrorMessage(error instanceof UserFacingError ? error.message : fallback);
}

function toDisplayLabel(value: string): string {
  return value.length === 0 ? value : `${value.charAt(0).toUpperCase()}${value.slice(1)}`;
}

function formatCommitDetail(detail: GitCommitDetail): string {
  return [
    `Commit: ${detail.shortSha}`,
    "",
    `Title: ${detail.subject}`,
    "",
    `Author: ${detail.author}`,
    "",
    `Date: ${detail.date}`,
    "",
    "Changed files:",
    ...detail.changedFiles.map((file) => `  ${file}`)
  ].join("\n");
}
