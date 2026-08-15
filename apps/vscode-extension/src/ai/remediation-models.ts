import type { AiEvidenceReference, GroundedPrompt } from "./models";
import type { GuardianFinding } from "../guardian/models";

export type RemediationConfidence = "low" | "medium" | "high";

export interface RemediationChange {
  readonly filePath: string;
  readonly startLine: number;
  readonly endLine: number;
  readonly expectedOriginalText: string;
  readonly replacementText: string;
  readonly reason: string;
}

export interface RemediationResponse {
  readonly summary: string;
  readonly rationale: string;
  readonly confidence: RemediationConfidence;
  readonly evidenceReferences: readonly AiEvidenceReference[];
  readonly changes: readonly RemediationChange[];
  readonly warnings: readonly string[];
}

export interface RemediationPrompt extends GroundedPrompt {
  readonly finding: GuardianFinding;
}

export interface RemediationProposal {
  readonly finding: GuardianFinding;
  readonly response: RemediationResponse;
  readonly sourceContentHashes: ReadonlyMap<string, string>;
  readonly provider: string;
  readonly model: string;
}
