import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export const PROJECT_MEMORY_SOURCE_TYPES = [
  "PROJECT",
  "CONSTITUTION",
  "RULE",
  "DECISION",
  "GIT_COMMIT",
  "GIT_STATUS",
  "GIT_FILE"
] as const;

export type ProjectMemorySourceType = (typeof PROJECT_MEMORY_SOURCE_TYPES)[number];

export interface ProjectMemoryDocument {
  readonly id: string;
  readonly sourceType: ProjectMemorySourceType;
  readonly title: string;
  readonly path: string | undefined;
  readonly content: string;
  readonly metadata: Readonly<Record<string, string>>;
  readonly updatedAt: string;
  readonly workspaceFolder: WorkspaceFolderInfo;
}

export interface ProjectMemorySearchResult {
  readonly document: ProjectMemoryDocument;
  readonly score: number;
  readonly matchedFields: readonly string[];
  readonly matchedTerms: readonly string[];
}

export interface ProjectMemoryIndex {
  readonly documents: readonly ProjectMemoryDocument[];
  readonly builtAt: string;
}
