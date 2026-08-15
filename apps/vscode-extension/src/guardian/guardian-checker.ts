import { join } from "node:path";

import { ruleAppliesToGuardianTarget } from "./scope";
import {
  databaseConflictTermsFor,
  extractRulePatterns,
  meaningfulRuleTerms,
  normalizeTerm
} from "./rule-patterns";

import type { GuardianContext, GuardianEvidence, GuardianFinding, GuardianCheckResult } from "./models";
import type { GuardianRulePattern } from "./rule-patterns";
import type { GitFileStatus } from "../git/models";
import type { ProjectMemorySearchResult } from "../memory/models";
import type { ArchitecturalDecision } from "../project-source/decisions";
import type { ExplicitRule } from "../project-source/rules";

export class DeterministicGuardianChecker {
  public check(context: GuardianContext): GuardianCheckResult {
    const findings = context.activeRules
      .filter((rule) => rule.isActive)
      .filter((rule) => ruleAppliesToGuardianTarget(rule, scopeTargetFor(context)))
      .flatMap((rule) => this.findRuleConflicts(rule, context));

    return {
      findings,
      checkedRules: context.activeRules.filter((rule) => rule.isActive).length,
      checkedDecisions: context.relevantDecisions.length,
      checkedFiles: context.codeContextPackage === undefined ? context.changedFiles.length : 1
    };
  }

  private findRuleConflicts(rule: ExplicitRule, context: GuardianContext): readonly GuardianFinding[] {
    return extractRulePatterns(rule)
      .map((pattern) => this.matchPattern(rule, pattern, context))
      .filter((finding): finding is GuardianFinding => finding !== undefined);
  }

  private matchPattern(
    rule: ExplicitRule,
    pattern: GuardianRulePattern,
    context: GuardianContext
  ): GuardianFinding | undefined {
    const evidenceText = guardianEvidenceText(context);
    switch (pattern.kind) {
      case "FORBIDDEN_TERM":
      case "FORBIDDEN_IMPORT":
      case "FORBIDDEN_DEPENDENCY": {
        const matchedTerms = pattern.aliases.filter((term) => containsTerm(evidenceText, term));
        if (matchedTerms.length === 0) {
          return undefined;
        }
        return this.createFinding(rule, context, pattern, matchedTerms);
      }
      case "REQUIRED_TECHNOLOGY": {
        const conflicts = databaseConflictTermsFor(pattern.primaryTerm).filter((term) => containsTerm(evidenceText, term));
        if (conflicts.length === 0) {
          return undefined;
        }
        if (pattern.requiresPersistenceContext && !hasPersistenceSignal(context, evidenceText)) {
          return undefined;
        }
        return this.createFinding(rule, context, pattern, conflicts);
      }
      case "REQUIRED_ARCHITECTURAL_COMPONENT": {
        if (containsTerm(evidenceText, pattern.primaryTerm.toLowerCase())) {
          return undefined;
        }
        const directAuthTerms = DIRECT_AUTH_IMPLEMENTATION_TERMS.filter((term) => containsTerm(evidenceText, term));
        if (directAuthTerms.length < 2) {
          return undefined;
        }
        return this.createFinding(rule, context, pattern, directAuthTerms);
      }
    }
  }

  private createFinding(
    rule: ExplicitRule,
    context: GuardianContext,
    pattern: GuardianRulePattern,
    matchedTerms: readonly string[]
  ): GuardianFinding {
    const codeEvidence = codeEvidenceFor(context, matchedTerms);
    const evidence = [
      {
        kind: "RULE",
        title: rule.title,
        detail: rule.ruleText,
        path: rule.path
      },
      ...codeEvidence,
      ...decisionEvidenceFor(rule, context.relevantDecisions, matchedTerms),
      ...memoryEvidenceFor(context.relevantProjectMemory, [pattern.primaryTerm, ...matchedTerms])
    ] satisfies readonly GuardianEvidence[];

    const codeContext = context.codeContextPackage?.codeContext;
    const base = {
      id: stableFindingId(rule, pattern.kind, matchedTerms, context),
      severity: "WARNING" as const,
      title: "Potential project-rule conflict",
      message: findingMessage(pattern, rule, matchedTerms),
      ruleId: rule.id,
      ruleTitle: rule.title,
      rulePath: rule.path,
      matchedTerms: uniqueStrings(matchedTerms.map(normalizeTerm)),
      evidence,
      source: context.source,
      workspaceFolder: context.workspaceFolder
    };

    if (codeContext !== undefined) {
      return {
        ...base,
        codePath: codeContext.filePath,
        codeStartLine: codeContext.startLine,
        codeEndLine: codeContext.endLine
      };
    }

    const firstChangedFile = firstMatchingChangedFile(context.changedFiles, matchedTerms);
    return firstChangedFile === undefined
      ? base
      : {
          ...base,
          codePath: join(context.workspaceFolder.fsPath, firstChangedFile.path)
        };
  }
}

function guardianEvidenceText(context: GuardianContext): string {
  const codeContext = context.codeContextPackage?.codeContext;
  const codeText = codeContext === undefined
    ? ""
    : [
        codeContext.relativePath,
        codeContext.fileName,
        codeContext.language,
        codeContext.symbolName ?? "",
        codeContext.selectedText,
        codeContext.surroundingCode,
        ...codeContext.relatedImports
      ].join("\n");
  const changedFileText = context.changedFiles.map((file) => `${file.path}\n${file.previousPath ?? ""}`).join("\n");
  return `${codeText}\n${changedFileText}`.toLowerCase();
}

function scopeTargetFor(context: GuardianContext): Parameters<typeof ruleAppliesToGuardianTarget>[1] {
  const codeContext = context.codeContextPackage?.codeContext;
  if (codeContext === undefined) {
    return {
      workspaceFolder: context.workspaceFolder,
      changedFiles: context.changedFiles
    };
  }

  return {
    workspaceFolder: context.workspaceFolder,
    codeContext,
    changedFiles: context.changedFiles
  };
}

function hasPersistenceSignal(context: GuardianContext, evidenceText: string): boolean {
  if (/\b(repository|persistence|persistent|database|storage|datasource|entitymanager|collection)\b/i.test(evidenceText)) {
    return true;
  }

  return context.changedFiles.some((file) => /(^|[/\\])(repository|persistence|storage|database|db)([/\\]|\.|-|_)/i.test(file.path));
}

function codeEvidenceFor(context: GuardianContext, matchedTerms: readonly string[]): readonly GuardianEvidence[] {
  const codeContext = context.codeContextPackage?.codeContext;
  if (codeContext !== undefined) {
    return [
      {
        kind: "CODE",
        title: codeContext.relativePath,
        detail: `Matched selected-code evidence: ${matchedTerms.join(", ")}. Lines ${codeContext.startLine}-${codeContext.endLine}.`,
        path: codeContext.filePath
      }
    ];
  }

  return context.changedFiles
    .filter((file) => matchedTerms.some((term) => containsTerm(file.path, term)))
    .map((file) => ({
      kind: "GIT_FILE" as const,
      title: file.path,
      detail: changedFileDetail(file, matchedTerms),
      path: file.path
    }));
}

function decisionEvidenceFor(
  rule: ExplicitRule,
  decisions: readonly ArchitecturalDecision[],
  matchedTerms: readonly string[]
): readonly GuardianEvidence[] {
  const ruleTerms = new Set([...meaningfulRuleTerms(rule), ...matchedTerms.map(normalizeTerm)]);
  return decisions
    .filter((decision) => decision.status === "Accepted")
    .filter((decision) => titleHasAnyTerm(decision.title, ruleTerms))
    .map((decision) => ({
      kind: "DECISION" as const,
      title: decision.title,
      detail: "Accepted decision with overlapping deterministic terms.",
      path: decision.path
    }));
}

function memoryEvidenceFor(
  memoryResults: readonly ProjectMemorySearchResult[],
  matchedTerms: readonly string[]
): readonly GuardianEvidence[] {
  return memoryResults
    .filter((result) => result.document.sourceType === "RULE" || result.document.sourceType === "DECISION")
    .filter((result) => result.matchedTerms.some((term) => matchedTerms.includes(normalizeTerm(term))))
    .slice(0, 3)
    .map((result) => {
      const evidence = {
        kind: "MEMORY" as const,
        title: result.document.title,
        detail: `Project Memory matched fields: ${result.matchedFields.join(", ")}.`
      };
      return result.document.path === undefined ? evidence : { ...evidence, path: result.document.path };
    });
}

function firstMatchingChangedFile(
  changedFiles: readonly GitFileStatus[],
  matchedTerms: readonly string[]
): GitFileStatus | undefined {
  return changedFiles.find((file) => matchedTerms.some((term) => containsTerm(file.path, term)));
}

function containsTerm(text: string, term: string): boolean {
  const normalizedTerm = normalizeTerm(term);
  if (normalizedTerm.length === 0) {
    return false;
  }

  return new RegExp(`(^|[^a-z0-9])${escapeRegExp(normalizedTerm)}([^a-z0-9]|$)`, "i").test(text);
}

function titleHasAnyTerm(title: string, terms: ReadonlySet<string>): boolean {
  const normalizedTitle = title.toLowerCase();
  return [...terms].some((term) => containsTerm(normalizedTitle, term));
}

function findingMessage(pattern: GuardianRulePattern, rule: ExplicitRule, matchedTerms: readonly string[]): string {
  switch (pattern.kind) {
    case "FORBIDDEN_TERM":
    case "FORBIDDEN_IMPORT":
    case "FORBIDDEN_DEPENDENCY":
      return `This change references ${matchedTerms.join(", ")}, while the project rule "${rule.title}" restricts it.`;
    case "REQUIRED_TECHNOLOGY":
      return `This change references ${matchedTerms.join(", ")}, while the project rule "${rule.title}" requires ${pattern.primaryTerm}.`;
    case "REQUIRED_ARCHITECTURAL_COMPONENT":
      return `This change contains direct implementation signals (${matchedTerms.join(", ")}) but does not reference ${pattern.primaryTerm}.`;
  }
}

function changedFileDetail(file: GitFileStatus, matchedTerms: readonly string[]): string {
  const states = [
    file.staged ? "staged" : undefined,
    file.unstaged ? "unstaged" : undefined,
    file.untracked ? "untracked" : undefined,
    file.deleted ? "deleted" : undefined,
    file.renamed ? "renamed" : undefined
  ].filter((state): state is string => state !== undefined);

  return `Changed file matched ${matchedTerms.join(", ")}${states.length === 0 ? "." : ` (${states.join(", ")}).`}`;
}

function stableFindingId(
  rule: ExplicitRule,
  patternKind: string,
  matchedTerms: readonly string[],
  context: GuardianContext
): string {
  const location = context.codeContextPackage?.codeContext.relativePath ?? context.changedFiles.map((file) => file.path).join("|");
  return `${rule.id}:${patternKind}:${uniqueStrings(matchedTerms.map(normalizeTerm)).join(",")}:${location}`;
}

function uniqueStrings(values: readonly string[]): readonly string[] {
  return [...new Set(values.filter((value) => value.length > 0))];
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const DIRECT_AUTH_IMPLEMENTATION_TERMS = [
  "jwt",
  "jwtvalidator",
  "bearer",
  "bearertoken",
  "validatetoken",
  "parseclaims",
  "authenticationmanager",
  "passwordencoder",
  "setauthentication"
];
