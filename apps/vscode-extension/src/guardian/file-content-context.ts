import { basename } from "node:path";

import { languageForPath } from "./file-relevance";

import type { CodeContextPackage } from "../code-context/models";
import type { ProjectMemorySearchResult } from "../memory/models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

const MAX_AUTOMATIC_CONTEXT_CHARACTERS = 200_000;

export interface GuardianFileContent {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly filePath: string;
  readonly relativePath: string;
  readonly content: string;
}

export function buildFileContentCodeContextPackage(
  file: GuardianFileContent,
  memoryResults: readonly ProjectMemorySearchResult[] = []
): CodeContextPackage {
  const boundedContent = file.content.slice(0, MAX_AUTOMATIC_CONTEXT_CHARACTERS);
  const lineCount = Math.max(1, boundedContent.split(/\r?\n/).length);

  return {
    codeContext: {
      workspaceFolder: file.workspaceFolder,
      filePath: file.filePath,
      relativePath: file.relativePath,
      fileName: basename(file.relativePath),
      language: languageForPath(file.relativePath),
      symbolName: undefined,
      symbolKind: undefined,
      startLine: 1,
      endLine: lineCount,
      selectedText: boundedContent,
      selectedTextPreview: boundedContent.slice(0, 4_000),
      isSelectionPreviewTruncated: boundedContent.length > 4_000,
      surroundingCode: boundedContent,
      surroundingStartLine: 1,
      surroundingEndLine: lineCount,
      relatedImports: extractImportLikeLines(boundedContent)
    },
    projectEvidence: memoryResults.filter((result) =>
      ["PROJECT", "CONSTITUTION", "RULE", "DECISION"].includes(result.document.sourceType)
    ),
    gitEvidence: {
      repository: undefined,
      fileStatus: undefined,
      relevantCommits: [],
      unavailableReason: undefined
    },
    memoryResults,
    createdAt: new Date(0).toISOString()
  };
}

export function automaticGuardianMemoryQuery(file: GuardianFileContent): string {
  return [
    basename(file.relativePath),
    ...identifierTerms(file.content).slice(0, 20)
  ].join(" ");
}

function extractImportLikeLines(content: string): readonly string[] {
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) =>
      /^(import\s|import\s*\(|from\s|require\s*\(|using\s|#include\s|package\s|namespace\s)/.test(line)
    )
    .slice(0, 100);
}

function identifierTerms(content: string): readonly string[] {
  return [...new Set(content.match(/[A-Za-z_][A-Za-z0-9_]*/g) ?? [])]
    .filter((term) => term.length > 2)
    .slice(0, 100);
}
