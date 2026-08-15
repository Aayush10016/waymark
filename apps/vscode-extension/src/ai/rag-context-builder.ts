import type { CodeContextPackage } from "../code-context/models";
import type { GitCommitSummary, GitFileStatus } from "../git/models";
import type { ProjectMemorySearcher } from "../code-context/code-context-package-builder";
import type { ProjectMemorySearchResult } from "../memory/models";
import type { AiEvidence, GroundedRagContext } from "./models";

export interface RagContextBuilderOptions {
  readonly maxEvidenceItems?: number;
  readonly maxTotalCharacters?: number;
}

export class GroundedRagContextBuilder {
  private readonly memoryRepository: ProjectMemorySearcher;
  private readonly maxEvidenceItems: number;
  private readonly maxTotalCharacters: number;

  public constructor(memoryRepository: ProjectMemorySearcher, options: RagContextBuilderOptions = {}) {
    this.memoryRepository = memoryRepository;
    this.maxEvidenceItems = options.maxEvidenceItems ?? 8;
    this.maxTotalCharacters = options.maxTotalCharacters ?? 12_000;
  }

  public async build(question: string, contextPackage: CodeContextPackage): Promise<GroundedRagContext> {
    const code = contextPackage.codeContext;
    const query = [question, code.symbolName, code.fileName, code.selectedTextPreview].filter(Boolean).join(" ");
    const retrievedMemory = await this.memoryRepository.search(query, [code.workspaceFolder]);
    const evidence = [
      codeEvidence(contextPackage),
      ...memoryEvidence([...contextPackage.memoryResults, ...retrievedMemory]),
      ...gitEvidence(contextPackage.gitEvidence.fileStatus, contextPackage.gitEvidence.relevantCommits, code.workspaceFolder)
    ];

    const ranked = rankEvidence(deduplicateEvidence(evidence));
    const budgeted = applyEvidenceBudget(ranked, this.maxEvidenceItems, this.maxTotalCharacters);

    return {
      question,
      evidence: budgeted,
      droppedEvidenceCount: ranked.length - budgeted.length
    };
  }
}

export function rankEvidence(evidence: readonly AiEvidence[]): readonly AiEvidence[] {
  return [...evidence].sort((left, right) => {
    const relevanceDelta = right.relevance - left.relevance;
    if (relevanceDelta !== 0) {
      return relevanceDelta;
    }
    const sourceDelta = sourceRank(right.sourceType) - sourceRank(left.sourceType);
    if (sourceDelta !== 0) {
      return sourceDelta;
    }
    return left.id.localeCompare(right.id);
  });
}

export function deduplicateEvidence(evidence: readonly AiEvidence[]): readonly AiEvidence[] {
  const byId = new Map<string, AiEvidence>();
  for (const item of evidence) {
    const existing = byId.get(item.id);
    if (existing === undefined || item.relevance > existing.relevance) {
      byId.set(item.id, item);
    }
  }
  return [...byId.values()];
}

export function applyEvidenceBudget(
  evidence: readonly AiEvidence[],
  maxEvidenceItems: number,
  maxTotalCharacters: number
): readonly AiEvidence[] {
  const selected: AiEvidence[] = [];
  let remainingCharacters = maxTotalCharacters;

  for (const item of evidence.slice(0, maxEvidenceItems)) {
    if (remainingCharacters <= 0) {
      break;
    }

    const content = item.content.length <= remainingCharacters
      ? item.content
      : item.content.slice(0, remainingCharacters);
    selected.push({ ...item, content });
    remainingCharacters -= content.length;
  }

  return selected;
}

function codeEvidence(contextPackage: CodeContextPackage): AiEvidence {
  const code = contextPackage.codeContext;
  return {
    id: "CODE-SELECTED",
    sourceType: "CODE",
    title: `${code.fileName}:${code.startLine}-${code.endLine}`,
    sourcePath: code.filePath,
    content: [
      `File: ${code.relativePath}`,
      `Language: ${code.language}`,
      `Symbol: ${code.symbolName ?? "(selection)"}`,
      `Lines: ${code.startLine}-${code.endLine}`,
      "",
      "Selected code:",
      code.selectedText,
      "",
      "Surrounding code:",
      code.surroundingCode,
      "",
      "Imports:",
      ...(code.relatedImports.length === 0 ? ["(none)"] : code.relatedImports)
    ].join("\n"),
    relevance: 10_000,
    workspaceFolder: code.workspaceFolder
  };
}

function memoryEvidence(results: readonly ProjectMemorySearchResult[]): readonly AiEvidence[] {
  return results.map((result) => ({
    id: result.document.id,
    sourceType: result.document.sourceType,
    title: result.document.title,
    sourcePath: result.document.path,
    content: result.document.content,
    relevance: result.score,
    workspaceFolder: result.document.workspaceFolder
  }));
}

function gitEvidence(
  fileStatus: GitFileStatus | undefined,
  commits: readonly GitCommitSummary[],
  workspaceFolder: AiEvidence["workspaceFolder"]
): readonly AiEvidence[] {
  const evidence: AiEvidence[] = [];
  if (fileStatus !== undefined) {
    evidence.push({
      id: `GIT-FILE-${fileStatus.path}`,
      sourceType: "GIT_FILE",
      title: fileStatus.path,
      sourcePath: fileStatus.path,
      content: `Changed file: ${fileStatus.path}`,
      relevance: 50,
      workspaceFolder
    });
  }

  for (const commit of commits) {
    evidence.push({
      id: `GIT-COMMIT-${commit.sha}`,
      sourceType: "GIT_COMMIT",
      title: `${commit.shortSha} ${commit.subject}`,
      sourcePath: undefined,
      content: `Commit ${commit.shortSha}: ${commit.subject}\nAuthor: ${commit.author}\nDate: ${commit.date}`,
      relevance: 30,
      workspaceFolder
    });
  }

  return evidence;
}

function sourceRank(sourceType: AiEvidence["sourceType"]): number {
  switch (sourceType) {
    case "CODE":
      return 100;
    case "RULE":
      return 90;
    case "DECISION":
      return 80;
    case "CONSTITUTION":
      return 70;
    case "PROJECT":
      return 60;
    case "GIT_FILE":
      return 50;
    case "GIT_COMMIT":
      return 40;
    case "GIT_STATUS":
      return 30;
  }
}
