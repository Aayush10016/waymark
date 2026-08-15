import type { ProjectMemorySearchResult } from "../memory/models";
import type { GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "../git/models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export interface CodeContext {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly filePath: string;
  readonly relativePath: string;
  readonly fileName: string;
  readonly language: string;
  readonly symbolName: string | undefined;
  readonly symbolKind: string | undefined;
  readonly startLine: number;
  readonly endLine: number;
  readonly selectedText: string;
  readonly selectedTextPreview: string;
  readonly isSelectionPreviewTruncated: boolean;
  readonly surroundingCode: string;
  readonly surroundingStartLine: number;
  readonly surroundingEndLine: number;
  readonly relatedImports: readonly string[];
}

export interface CodeGitContext {
  readonly repository: GitRepositoryInfo | undefined;
  readonly fileStatus: GitFileStatus | undefined;
  readonly relevantCommits: readonly GitCommitSummary[];
  readonly unavailableReason: string | undefined;
}

export interface CodeContextPackage {
  readonly codeContext: CodeContext;
  readonly projectEvidence: readonly ProjectMemorySearchResult[];
  readonly gitEvidence: CodeGitContext;
  readonly memoryResults: readonly ProjectMemorySearchResult[];
  readonly createdAt: string;
}

export interface SourceSelection {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly filePath: string;
  readonly relativePath: string;
  readonly fileName: string;
  readonly language: string;
  readonly fullText: string;
  readonly selectedText: string;
  readonly startLine: number;
  readonly endLine: number;
  readonly selectionStartOffset: number;
  readonly selectionEndOffset: number;
  readonly symbols: readonly SourceSymbol[];
}

export interface SourceSymbol {
  readonly name: string;
  readonly kind: string;
  readonly startLine: number;
  readonly endLine: number;
  readonly children?: readonly SourceSymbol[];
}
