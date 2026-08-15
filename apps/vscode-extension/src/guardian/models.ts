import type { CodeContextPackage } from "../code-context/models";
import type { GitFileStatus } from "../git/models";
import type { ProjectMemorySearchResult } from "../memory/models";
import type { ArchitecturalDecision } from "../project-source/decisions";
import type { ExplicitRule } from "../project-source/rules";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export type GuardianSeverity = "INFO" | "WARNING";

export type GuardianFindingSource = "SELECTED_CODE" | "CURRENT_CHANGES" | "AUTOMATIC_FILE_CHANGE";

export type GuardianEvidenceKind = "RULE" | "CODE" | "GIT_FILE" | "DECISION" | "MEMORY";

export interface GuardianEvidence {
  readonly kind: GuardianEvidenceKind;
  readonly title: string;
  readonly detail: string;
  readonly path?: string;
}

export interface GuardianFinding {
  readonly id: string;
  readonly severity: GuardianSeverity;
  readonly title: string;
  readonly message: string;
  readonly ruleId: string;
  readonly ruleTitle: string;
  readonly rulePath: string;
  readonly matchedTerms: readonly string[];
  readonly evidence: readonly GuardianEvidence[];
  readonly source: GuardianFindingSource;
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly codePath?: string;
  readonly codeStartLine?: number;
  readonly codeEndLine?: number;
}

export interface GuardianContext {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly source: GuardianFindingSource;
  readonly codeContextPackage?: CodeContextPackage;
  readonly changedFiles: readonly GitFileStatus[];
  readonly activeRules: readonly ExplicitRule[];
  readonly relevantDecisions: readonly ArchitecturalDecision[];
  readonly relevantProjectMemory: readonly ProjectMemorySearchResult[];
}

export interface GuardianCheckResult {
  readonly findings: readonly GuardianFinding[];
  readonly checkedRules: number;
  readonly checkedDecisions: number;
  readonly checkedFiles: number;
}
