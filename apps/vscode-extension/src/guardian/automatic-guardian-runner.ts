import { DeterministicGuardianChecker } from "./guardian-checker";
import { automaticGuardianMemoryQuery, buildFileContentCodeContextPackage } from "./file-content-context";
import { isAutomaticGuardianRelevantPath } from "./file-relevance";

import type { ProjectMemorySearcher } from "../code-context/code-context-package-builder";
import type { ArchitecturalDecision } from "../project-source/decisions";
import type { ExplicitRule } from "../project-source/rules";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";
import type { GuardianCheckResult, GuardianContext, GuardianFinding } from "./models";
import type { GuardianFileContent } from "./file-content-context";

export interface AutomaticGuardianRuleReader {
  list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ExplicitRule[]>;
}

export interface AutomaticGuardianDecisionReader {
  list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ArchitecturalDecision[]>;
}

export interface AutomaticGuardianFileResult {
  readonly file: GuardianFileContent;
  readonly result: GuardianCheckResult;
}

export interface AutomaticGuardianBatchResult {
  readonly fileResults: readonly AutomaticGuardianFileResult[];
  readonly findings: readonly GuardianFinding[];
}

export class AutomaticGuardianRunner {
  private readonly ruleReader: AutomaticGuardianRuleReader;
  private readonly decisionReader: AutomaticGuardianDecisionReader;
  private readonly memoryRepository: ProjectMemorySearcher;
  private readonly checker: DeterministicGuardianChecker;

  public constructor(
    ruleReader: AutomaticGuardianRuleReader,
    decisionReader: AutomaticGuardianDecisionReader,
    memoryRepository: ProjectMemorySearcher,
    checker: DeterministicGuardianChecker = new DeterministicGuardianChecker()
  ) {
    this.ruleReader = ruleReader;
    this.decisionReader = decisionReader;
    this.memoryRepository = memoryRepository;
    this.checker = checker;
  }

  public async checkFiles(files: readonly GuardianFileContent[]): Promise<AutomaticGuardianBatchResult> {
    const relevantFiles = files.filter((file) => isAutomaticGuardianRelevantPath(file.relativePath));
    const fileResults = await Promise.all(relevantFiles.map((file) => this.checkFile(file)));

    return {
      fileResults,
      findings: fileResults.flatMap((fileResult) => fileResult.result.findings)
    };
  }

  private async checkFile(file: GuardianFileContent): Promise<AutomaticGuardianFileResult> {
    const [rules, decisions, memoryResults] = await Promise.all([
      this.ruleReader.list(file.workspaceFolder),
      this.decisionReader.list(file.workspaceFolder),
      this.memoryRepository.search(automaticGuardianMemoryQuery(file), [file.workspaceFolder])
    ]);
    const codeContextPackage = buildFileContentCodeContextPackage(file, memoryResults);
    const context: GuardianContext = {
      workspaceFolder: file.workspaceFolder,
      source: "AUTOMATIC_FILE_CHANGE",
      codeContextPackage,
      changedFiles: [],
      activeRules: rules.filter((rule) => rule.isActive),
      relevantDecisions: decisions.filter((decision) => decision.status === "Accepted"),
      relevantProjectMemory: memoryResults
    };

    return {
      file,
      result: this.checker.check(context)
    };
  }
}
