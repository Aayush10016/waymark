import type { ProjectMemorySourceType } from "../memory/models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export type AiEvidenceSourceType = ProjectMemorySourceType | "CODE";

export interface AiEvidence {
  readonly id: string;
  readonly sourceType: AiEvidenceSourceType;
  readonly title: string;
  readonly sourcePath: string | undefined;
  readonly content: string;
  readonly relevance: number;
  readonly workspaceFolder: WorkspaceFolderInfo;
}

export interface GroundedRagContext {
  readonly question: string;
  readonly evidence: readonly AiEvidence[];
  readonly droppedEvidenceCount: number;
}

export interface GroundedPrompt {
  readonly system: string;
  readonly user: string;
  readonly evidence: readonly AiEvidence[];
}

export interface AiEvidenceReference {
  readonly evidenceId: string;
  readonly title: string;
  readonly sourcePath: string | undefined;
  readonly sourceType: AiEvidenceSourceType;
}

export interface GroundedAiResponse {
  readonly answer: string;
  readonly evidenceReferences: readonly AiEvidenceReference[];
  readonly provider: string;
  readonly model: string;
  readonly grounded: boolean;
  readonly warnings: readonly string[];
}
