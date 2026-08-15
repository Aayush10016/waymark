import { AiProviderError } from "./provider";

import type { AiEvidenceReference, GroundedPrompt } from "./models";
import type { AiProviderResult } from "./provider";
import type { RemediationChange, RemediationConfidence, RemediationResponse } from "./remediation-models";

export function buildRemediationResponse(providerResult: AiProviderResult, prompt: GroundedPrompt): RemediationResponse {
  const parsed = parseJsonObject(providerResult.text);
  const response = toRemediationResponse(parsed, prompt);
  return response;
}

function parseJsonObject(text: string): Record<string, unknown> {
  const trimmed = stripJsonFence(text.trim());
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI returned malformed remediation JSON.");
  }

  if (!isRecord(parsed)) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI remediation response must be a JSON object.");
  }

  return parsed;
}

function stripJsonFence(text: string): string {
  const match = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(text);
  return match?.[1] ?? text;
}

function toRemediationResponse(value: Record<string, unknown>, prompt: GroundedPrompt): RemediationResponse {
  const summary = requiredString(value.summary, "summary");
  const rationale = requiredString(value.rationale, "rationale");
  const confidence = requiredConfidence(value.confidence);
  const warnings = optionalStringArray(value.warnings, "warnings");
  const changes = requiredChangeArray(value.changes);
  const evidenceReferences = evidenceReferencesFor(value.evidenceReferences, prompt);

  if (confidence !== "low" && changes.length === 0) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI remediation response has confidence without proposed changes.");
  }

  return {
    summary,
    rationale,
    confidence,
    evidenceReferences,
    changes,
    warnings: evidenceReferences.length === 0
      ? [...warnings, "The response did not cite supplied Waymark evidence IDs. Treat this proposal cautiously."]
      : warnings
  };
}

function requiredChangeArray(value: unknown): readonly RemediationChange[] {
  if (!Array.isArray(value)) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI remediation response changes must be an array.");
  }

  return value.map((item, index) => {
    if (!isRecord(item)) {
      throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation change ${index + 1} must be an object.`);
    }

    const startLine = requiredPositiveInteger(item.startLine, `changes[${index}].startLine`);
    const endLine = requiredPositiveInteger(item.endLine, `changes[${index}].endLine`);
    if (endLine < startLine) {
      throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation change ${index + 1} has an invalid line range.`);
    }

    return {
      filePath: requiredString(item.filePath, `changes[${index}].filePath`),
      startLine,
      endLine,
      expectedOriginalText: requiredString(item.expectedOriginalText, `changes[${index}].expectedOriginalText`),
      replacementText: requiredStringAllowingEmpty(item.replacementText, `changes[${index}].replacementText`),
      reason: requiredString(item.reason, `changes[${index}].reason`)
    };
  });
}

function evidenceReferencesFor(value: unknown, prompt: GroundedPrompt): readonly AiEvidenceReference[] {
  const ids = optionalStringArray(value, "evidenceReferences");
  const references = ids
    .map((id) => prompt.evidence.find((evidence) => evidence.id === id))
    .filter((evidence): evidence is GroundedPrompt["evidence"][number] => evidence !== undefined)
    .map((evidence) => ({
      evidenceId: evidence.id,
      title: evidence.title,
      sourcePath: evidence.sourcePath,
      sourceType: evidence.sourceType
    }));

  return references;
}

function requiredString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation response field ${fieldName} must be a non-empty string.`);
  }
  return value;
}

function requiredStringAllowingEmpty(value: unknown, fieldName: string): string {
  if (typeof value !== "string") {
    throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation response field ${fieldName} must be a string.`);
  }
  return value;
}

function requiredPositiveInteger(value: unknown, fieldName: string): number {
  if (!Number.isInteger(value) || typeof value !== "number" || value < 1) {
    throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation response field ${fieldName} must be a positive integer.`);
  }
  return value;
}

function requiredConfidence(value: unknown): RemediationConfidence {
  if (value === "low" || value === "medium" || value === "high") {
    return value;
  }
  throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI remediation response confidence must be low, medium, or high.");
}

function optionalStringArray(value: unknown, fieldName: string): readonly string[] {
  if (value === undefined) {
    return [];
  }
  if (!Array.isArray(value) || !value.every((item) => typeof item === "string")) {
    throw new AiProviderError("MALFORMED_RESPONSE", `Waymark AI remediation response field ${fieldName} must be a string array.`);
  }
  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
