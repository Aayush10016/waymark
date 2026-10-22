import { createHash } from "node:crypto";

import type { RemediationChange } from "./remediation-models";

export interface RemediationEditValidationFailure {
  readonly code:
    | "NO_CHANGES"
    | "UNRELATED_FILE"
    | "FILE_CHANGED"
    | "INVALID_LINE_RANGE"
    | "OVERLAPPING_CHANGES"
    | "EXPECTED_TEXT_MISMATCH";
  readonly message: string;
}

export interface ValidatedRemediationChange {
  readonly change: RemediationChange;
}

export interface RemediationEditValidationSuccess {
  readonly ok: true;
  readonly changes: readonly ValidatedRemediationChange[];
}

export interface RemediationEditValidationError {
  readonly ok: false;
  readonly failure: RemediationEditValidationFailure;
}

export type RemediationEditValidationResult = RemediationEditValidationSuccess | RemediationEditValidationError;

export interface RemediationEditValidationInput {
  readonly intendedFilePath: string;
  readonly changes: readonly RemediationChange[];
  readonly currentContentsByFilePath: ReadonlyMap<string, string>;
  readonly expectedContentHashes: ReadonlyMap<string, string>;
}

export function contentHash(content: string): string {
  return createHash("sha256").update(normalizeNewlines(content), "utf8").digest("hex");
}

export function validateRemediationEdits(input: RemediationEditValidationInput): RemediationEditValidationResult {
  if (input.changes.length === 0) {
    return failure("NO_CHANGES", "Waymark AI did not propose an applicable edit.");
  }

  const normalizedIntendedPath = normalizePath(input.intendedFilePath);
  const validated: ValidatedRemediationChange[] = [];

  for (const change of input.changes) {
    if (normalizePath(change.filePath) !== normalizedIntendedPath) {
      return failure("UNRELATED_FILE", "Waymark refused to modify a file unrelated to this Guardian finding.");
    }

    const currentContent = valueForNormalizedPath(input.currentContentsByFilePath, change.filePath);
    const expectedHash = valueForNormalizedPath(input.expectedContentHashes, change.filePath);
    if (currentContent === undefined || expectedHash === undefined || contentHash(currentContent) !== expectedHash) {
      return failure("FILE_CHANGED", "Waymark refused to apply this proposal because the file changed after the proposal was generated.");
    }

    const currentText = lineRangeText(currentContent, change.startLine, change.endLine);
    if (currentText === undefined) {
      return failure("INVALID_LINE_RANGE", "Waymark refused to apply this proposal because its line range is no longer valid.");
    }

    if (normalizeNewlines(currentText) !== normalizeNewlines(change.expectedOriginalText)) {
      return failure("EXPECTED_TEXT_MISMATCH", "Waymark refused to apply this proposal because the expected original text no longer matches.");
    }

    validated.push({ change });
  }

  const ordered = [...input.changes].sort((left, right) => left.startLine - right.startLine);
  for (let index = 1; index < ordered.length; index += 1) {
    const previous = ordered[index - 1];
    const current = ordered[index];
    if (previous !== undefined && current !== undefined && current.startLine <= previous.endLine) {
      return failure("OVERLAPPING_CHANGES", "Waymark refused overlapping remediation edits. Request a new proposal.");
    }
  }

  return {
    ok: true,
    changes: validated
  };
}

export function applyRemediationChangesToText(content: string, changes: readonly RemediationChange[]): string {
  const lines = normalizeNewlines(content).split("\n");
  const orderedChanges = [...changes].sort((left, right) => right.startLine - left.startLine);
  for (const change of orderedChanges) {
    const replacementLines = normalizeNewlines(change.replacementText).split("\n");
    lines.splice(change.startLine - 1, change.endLine - change.startLine + 1, ...replacementLines);
  }
  return lines.join("\n");
}

export function lineRangeText(content: string, startLine: number, endLine: number): string | undefined {
  const lines = normalizeNewlines(content).split("\n");
  if (!Number.isSafeInteger(startLine) || !Number.isSafeInteger(endLine) || startLine < 1 || endLine < startLine || endLine > lines.length) {
    return undefined;
  }
  return lines.slice(startLine - 1, endLine).join("\n");
}

function failure(code: RemediationEditValidationFailure["code"], message: string): RemediationEditValidationError {
  return {
    ok: false,
    failure: {
      code,
      message
    }
  };
}

function normalizePath(path: string): string {
  return path.replace(/\\/g, "/").toLowerCase();
}

function normalizeNewlines(content: string): string {
  return content.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}

function valueForNormalizedPath<T>(values: ReadonlyMap<string, T>, path: string): T | undefined {
  const normalizedPath = normalizePath(path);
  for (const [key, value] of values) {
    if (normalizePath(key) === normalizedPath) {
      return value;
    }
  }
  return undefined;
}
