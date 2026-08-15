import { GitReadError } from "../git/git-client";

import type { CodeContextPackage } from "../code-context/models";
import type { GitClient } from "../git/git-client";
import type { ProjectMemorySearcher } from "../code-context/code-context-package-builder";
import type { ArchitecturalDecision } from "../project-source/decisions";
import type { ExplicitRule } from "../project-source/rules";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";
import type { GuardianContext } from "./models";

export type CurrentChangesGuardianContextResult =
  | {
      readonly kind: "ready";
      readonly context: GuardianContext;
    }
  | {
      readonly kind: "noChanges";
    }
  | {
      readonly kind: "gitUnavailable";
      readonly message: string;
    };

export interface GuardianRuleReader {
  list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ExplicitRule[]>;
}

export interface GuardianDecisionReader {
  list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ArchitecturalDecision[]>;
}

export class GuardianContextBuilder {
  private readonly ruleStore: GuardianRuleReader;
  private readonly decisionStore: GuardianDecisionReader;
  private readonly memoryRepository: ProjectMemorySearcher;
  private readonly gitClient: GitClient;

  public constructor(
    ruleStore: GuardianRuleReader,
    decisionStore: GuardianDecisionReader,
    memoryRepository: ProjectMemorySearcher,
    gitClient: GitClient
  ) {
    this.ruleStore = ruleStore;
    this.decisionStore = decisionStore;
    this.memoryRepository = memoryRepository;
    this.gitClient = gitClient;
  }

  public async buildForCodePackage(codeContextPackage: CodeContextPackage): Promise<GuardianContext> {
    const workspaceFolder = codeContextPackage.codeContext.workspaceFolder;
    const rules = await this.ruleStore.list(workspaceFolder);
    const decisions = await this.decisionStore.list(workspaceFolder);

    return {
      workspaceFolder,
      source: "SELECTED_CODE",
      codeContextPackage,
      changedFiles: [],
      activeRules: rules.filter((rule) => rule.isActive),
      relevantDecisions: decisions.filter((decision) => decision.status === "Accepted"),
      relevantProjectMemory: codeContextPackage.memoryResults
    };
  }

  public async buildForCurrentChanges(workspaceFolder: WorkspaceFolderInfo): Promise<CurrentChangesGuardianContextResult> {
    try {
      await this.gitClient.getRepositoryInfo(workspaceFolder);
      const changedFiles = await this.gitClient.getStatus(workspaceFolder);
      if (changedFiles.length === 0) {
        return { kind: "noChanges" };
      }

      const [rules, decisions, memoryResults] = await Promise.all([
        this.ruleStore.list(workspaceFolder),
        this.decisionStore.list(workspaceFolder),
        this.memoryRepository.search(changedFiles.map((file) => file.path).join(" "), [workspaceFolder])
      ]);

      return {
        kind: "ready",
        context: {
          workspaceFolder,
          source: "CURRENT_CHANGES",
          changedFiles,
          activeRules: rules.filter((rule) => rule.isActive),
          relevantDecisions: decisions.filter((decision) => decision.status === "Accepted"),
          relevantProjectMemory: memoryResults
        }
      };
    } catch (error) {
      if (error instanceof GitReadError) {
        return {
          kind: "gitUnavailable",
          message: error.code === "NOT_REPOSITORY"
            ? "Waymark Guardian requires a Git repository for current-change checking."
            : error.message
        };
      }

      throw error;
    }
  }
}
