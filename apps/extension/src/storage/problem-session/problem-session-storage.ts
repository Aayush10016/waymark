import type { ActiveProblemSessionPayload } from "@trail/extension-messages";

export const PROBLEM_SESSION_STORAGE_KEY = "trail.problemSession.runtime";
export const PROBLEM_SESSION_STORAGE_SCHEMA_VERSION = 1;

export interface StoredDismissedProblemSession {
  readonly activeSession: ActiveProblemSessionPayload;
  readonly id: string;
  readonly undoExpiresAt: string;
}

export interface StoredProblemSessionRuntime {
  readonly activeSession: ActiveProblemSessionPayload | null;
  readonly dismissedSession: StoredDismissedProblemSession | null;
  readonly pendingStartId: string | null;
  readonly schemaVersion: typeof PROBLEM_SESSION_STORAGE_SCHEMA_VERSION;
  readonly updatedAt: string;
}

export interface ProblemSessionRuntimeStorage {
  clear(): Promise<void>;
  read(): Promise<StoredProblemSessionRuntime | null>;
  write(state: StoredProblemSessionRuntime): Promise<void>;
}

export class ChromeLocalProblemSessionRuntimeStorage implements ProblemSessionRuntimeStorage {
  async clear(): Promise<void> {
    await chrome.storage.local.remove(PROBLEM_SESSION_STORAGE_KEY);
  }

  async read(): Promise<StoredProblemSessionRuntime | null> {
    const values = await chrome.storage.local.get(PROBLEM_SESSION_STORAGE_KEY);
    return parseStoredProblemSessionRuntime(values[PROBLEM_SESSION_STORAGE_KEY]);
  }

  async write(state: StoredProblemSessionRuntime): Promise<void> {
    await chrome.storage.local.set({ [PROBLEM_SESSION_STORAGE_KEY]: toPersistableRuntime(state) });
  }
}

export function createDefaultProblemSessionRuntime(now: Date): StoredProblemSessionRuntime {
  return freezeStoredProblemSessionRuntime({
    activeSession: null,
    dismissedSession: null,
    pendingStartId: null,
    schemaVersion: PROBLEM_SESSION_STORAGE_SCHEMA_VERSION,
    updatedAt: now.toISOString()
  });
}

export function parseStoredProblemSessionRuntime(value: unknown): StoredProblemSessionRuntime | null {
  if (!isRecord(value)) {
    return null;
  }

  if (
    value.schemaVersion !== PROBLEM_SESSION_STORAGE_SCHEMA_VERSION ||
    (value.activeSession !== null && !isActiveProblemSessionPayload(value.activeSession)) ||
    (value.dismissedSession !== null && !isDismissedProblemSession(value.dismissedSession)) ||
    (value.pendingStartId !== null && !isUuidLike(value.pendingStartId)) ||
    typeof value.updatedAt !== "string" ||
    Number.isNaN(Date.parse(value.updatedAt))
  ) {
    return null;
  }

  return freezeStoredProblemSessionRuntime({
    activeSession: value.activeSession,
    dismissedSession: value.dismissedSession,
    pendingStartId: value.pendingStartId,
    schemaVersion: PROBLEM_SESSION_STORAGE_SCHEMA_VERSION,
    updatedAt: value.updatedAt
  });
}

export function freezeStoredProblemSessionRuntime(state: StoredProblemSessionRuntime): StoredProblemSessionRuntime {
  return Object.freeze({
    activeSession: state.activeSession === null ? null : Object.freeze({ ...state.activeSession }),
    dismissedSession: state.dismissedSession === null ? null : Object.freeze({ ...state.dismissedSession }),
    pendingStartId: state.pendingStartId,
    schemaVersion: state.schemaVersion,
    updatedAt: state.updatedAt
  });
}

function toPersistableRuntime(state: StoredProblemSessionRuntime): StoredProblemSessionRuntime {
  return {
    activeSession: state.activeSession === null ? null : { ...state.activeSession },
    dismissedSession: state.dismissedSession === null ? null : { ...state.dismissedSession },
    pendingStartId: state.pendingStartId,
    schemaVersion: state.schemaVersion,
    updatedAt: state.updatedAt
  };
}

function isActiveProblemSessionPayload(value: unknown): value is ActiveProblemSessionPayload {
  return (
    isRecord(value) &&
    Array.isArray(value.detectionReasons) &&
    value.detectionReasons.every((reason) => typeof reason === "string") &&
    (value.endedAt === null || isRfc3339Instant(value.endedAt)) &&
    isNonBlankString(value.etag) &&
    isUuidLike(value.id) &&
    (value.source === "DETECTED" || value.source === "MANUAL") &&
    isRfc3339Instant(value.startedAt) &&
    isProblemSessionStatus(value.status) &&
    isNonBlankString(value.title) &&
    isRfc3339Instant(value.updatedAt) &&
    typeof value.version === "number" &&
    Number.isInteger(value.version) &&
    value.version >= 0
  );
}

function isDismissedProblemSession(value: unknown): value is StoredDismissedProblemSession {
  return (
    isRecord(value) &&
    isActiveProblemSessionPayload(value.activeSession) &&
    isUuidLike(value.id) &&
    isRfc3339Instant(value.undoExpiresAt)
  );
}

function isProblemSessionStatus(value: unknown): boolean {
  return (
    value === "CANDIDATE" ||
    value === "REVIEWABLE" ||
    value === "SOLVED" ||
    value === "DISMISSED" ||
    value === "UNSOLVED" ||
    value === "DELETED"
  );
}

function isRfc3339Instant(value: unknown): value is string {
  return isNonBlankString(value) && !Number.isNaN(Date.parse(value));
}

function isUuidLike(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
  );
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
