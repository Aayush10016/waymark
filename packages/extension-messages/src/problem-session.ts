import type { MessageEnvelope } from "./auth";

export const PROBLEM_SESSION_MESSAGE_TYPES = {
  dismiss: "PROBLEM_SESSION_DISMISS",
  finish: "PROBLEM_SESSION_FINISH",
  get: "PROBLEM_SESSION_GET",
  saveSolution: "PROBLEM_SESSION_SAVE_SOLUTION",
  start: "PROBLEM_SESSION_START",
  undoDismiss: "PROBLEM_SESSION_UNDO_DISMISS"
} as const;

export const PROBLEM_SESSION_RESPONSE_TYPES = {
  result: "PROBLEM_SESSION_RESULT"
} as const;

export type ProblemSessionMessageType =
  (typeof PROBLEM_SESSION_MESSAGE_TYPES)[keyof typeof PROBLEM_SESSION_MESSAGE_TYPES];

export type ProblemSessionStatus = "CANDIDATE" | "REVIEWABLE" | "SOLVED" | "DISMISSED" | "UNSOLVED" | "DELETED";
export type ProblemSessionSource = "DETECTED" | "MANUAL";
export type ProblemSessionReasonCategory =
  | "error_activity"
  | "manual_session"
  | "multiple_related_sources"
  | "repeated_activity"
  | "related_searches"
  | "session_boundary";

export interface ActiveProblemSessionPayload {
  readonly detectionReasons: readonly ProblemSessionReasonCategory[];
  readonly endedAt: string | null;
  readonly etag: string;
  readonly id: string;
  readonly source: ProblemSessionSource;
  readonly startedAt: string;
  readonly status: ProblemSessionStatus;
  readonly title: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface ProblemSessionRuntimePayload {
  readonly activeSession: ActiveProblemSessionPayload | null;
  readonly dismissedSession: {
    readonly id: string;
    readonly undoExpiresAt: string;
  } | null;
}

export type ProblemSessionCommandStatus =
  | "authentication_required"
  | "conflict"
  | "invalid_request"
  | "network_error"
  | "not_found"
  | "precondition_required"
  | "received"
  | "server_error"
  | "stale_version"
  | "unavailable";

export interface ProblemSessionResponsePayload {
  readonly commandStatus: ProblemSessionCommandStatus;
  readonly state: ProblemSessionRuntimePayload;
}

export type ProblemSessionGetMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.get,
  Record<string, never>
>;
export type ProblemSessionStartMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.start,
  {
    readonly title: string;
  }
>;
export type ProblemSessionFinishMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.finish,
  Record<string, never>
>;
export type ProblemSessionDismissMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.dismiss,
  Record<string, never>
>;
export type ProblemSessionUndoDismissMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.undoDismiss,
  Record<string, never>
>;
export type ProblemSessionSaveSolutionMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_MESSAGE_TYPES.saveSolution,
  {
    readonly finalSolution: string;
  }
>;

export type ProblemSessionRequestMessage =
  | ProblemSessionDismissMessage
  | ProblemSessionFinishMessage
  | ProblemSessionGetMessage
  | ProblemSessionSaveSolutionMessage
  | ProblemSessionStartMessage
  | ProblemSessionUndoDismissMessage;

export type ProblemSessionResponseMessage = MessageEnvelope<
  typeof PROBLEM_SESSION_RESPONSE_TYPES.result,
  ProblemSessionResponsePayload
>;

export type ProblemSessionMessageParseResult =
  | { readonly ok: true; readonly message: ProblemSessionRequestMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export type ProblemSessionResponseParseResult =
  | { readonly ok: true; readonly message: ProblemSessionResponseMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export function parseProblemSessionRequestMessage(input: unknown): ProblemSessionMessageParseResult {
  if (
    !isMessageEnvelope(input) ||
    !isProblemSessionRequestType(input.type) ||
    !isProblemSessionRequestPayload(input.type, input.payload)
  ) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: input.type,
      requestId: input.requestId,
      source: input.source,
      payload: problemSessionRequestPayload(input.type, input.payload)
    } as ProblemSessionRequestMessage
  };
}

export function parseProblemSessionResponseMessage(input: unknown): ProblemSessionResponseParseResult {
  if (
    !isMessageEnvelope(input) ||
    input.type !== PROBLEM_SESSION_RESPONSE_TYPES.result ||
    !isProblemSessionResponsePayload(input.payload)
  ) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: PROBLEM_SESSION_RESPONSE_TYPES.result,
      requestId: input.requestId,
      source: input.source,
      payload: input.payload
    }
  };
}

export function problemSessionResponse(
  request: ProblemSessionRequestMessage,
  payload: ProblemSessionResponsePayload
): ProblemSessionResponseMessage {
  return {
    schemaVersion: 1,
    type: PROBLEM_SESSION_RESPONSE_TYPES.result,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

function isProblemSessionRequestType(value: unknown): value is ProblemSessionMessageType {
  return (
    value === PROBLEM_SESSION_MESSAGE_TYPES.dismiss ||
    value === PROBLEM_SESSION_MESSAGE_TYPES.finish ||
    value === PROBLEM_SESSION_MESSAGE_TYPES.get ||
    value === PROBLEM_SESSION_MESSAGE_TYPES.saveSolution ||
    value === PROBLEM_SESSION_MESSAGE_TYPES.start ||
    value === PROBLEM_SESSION_MESSAGE_TYPES.undoDismiss
  );
}

function isProblemSessionRequestPayload(
  type: ProblemSessionMessageType,
  payload: Record<string, unknown>
): boolean {
  if (type === PROBLEM_SESSION_MESSAGE_TYPES.start) {
    return isNonBlankString(payload.title) && Object.keys(payload).length === 1;
  }

  if (type === PROBLEM_SESSION_MESSAGE_TYPES.saveSolution) {
    return isNonBlankString(payload.finalSolution) && Object.keys(payload).length === 1;
  }

  return Object.keys(payload).length === 0;
}

function problemSessionRequestPayload(
  type: ProblemSessionMessageType,
  payload: Record<string, unknown>
): ProblemSessionRequestMessage["payload"] {
  if (type === PROBLEM_SESSION_MESSAGE_TYPES.start) {
    return { title: payload.title as string };
  }

  if (type === PROBLEM_SESSION_MESSAGE_TYPES.saveSolution) {
    return { finalSolution: payload.finalSolution as string };
  }

  return {};
}

function isProblemSessionResponsePayload(value: unknown): value is ProblemSessionResponsePayload {
  return (
    isRecord(value) &&
    isProblemSessionCommandStatus(value.commandStatus) &&
    isProblemSessionRuntimePayload(value.state) &&
    Object.keys(value).length === 2
  );
}

function isProblemSessionRuntimePayload(value: unknown): value is ProblemSessionRuntimePayload {
  return (
    isRecord(value) &&
    (value.activeSession === null || isActiveProblemSessionPayload(value.activeSession)) &&
    (value.dismissedSession === null || isDismissedSessionPayload(value.dismissedSession)) &&
    Object.keys(value).length === 2
  );
}

function isActiveProblemSessionPayload(value: unknown): value is ActiveProblemSessionPayload {
  return (
    isRecord(value) &&
    Array.isArray(value.detectionReasons) &&
    value.detectionReasons.every(isProblemSessionReasonCategory) &&
    (value.endedAt === null || isRfc3339Instant(value.endedAt)) &&
    isNonBlankString(value.etag) &&
    isUuidLike(value.id) &&
    isProblemSessionSource(value.source) &&
    isRfc3339Instant(value.startedAt) &&
    isProblemSessionStatus(value.status) &&
    isNonBlankString(value.title) &&
    isRfc3339Instant(value.updatedAt) &&
    typeof value.version === "number" &&
    Number.isInteger(value.version) &&
    value.version >= 0 &&
    Object.keys(value).length === 10
  );
}

function isDismissedSessionPayload(value: unknown): value is ProblemSessionRuntimePayload["dismissedSession"] {
  return isRecord(value) && isUuidLike(value.id) && isRfc3339Instant(value.undoExpiresAt) && Object.keys(value).length === 2;
}

function isProblemSessionCommandStatus(value: unknown): value is ProblemSessionCommandStatus {
  return (
    value === "authentication_required" ||
    value === "conflict" ||
    value === "invalid_request" ||
    value === "network_error" ||
    value === "not_found" ||
    value === "precondition_required" ||
    value === "received" ||
    value === "server_error" ||
    value === "stale_version" ||
    value === "unavailable"
  );
}

function isProblemSessionReasonCategory(value: unknown): value is ProblemSessionReasonCategory {
  return (
    value === "error_activity" ||
    value === "manual_session" ||
    value === "multiple_related_sources" ||
    value === "repeated_activity" ||
    value === "related_searches" ||
    value === "session_boundary"
  );
}

function isProblemSessionSource(value: unknown): value is ProblemSessionSource {
  return value === "DETECTED" || value === "MANUAL";
}

function isProblemSessionStatus(value: unknown): value is ProblemSessionStatus {
  return (
    value === "CANDIDATE" ||
    value === "REVIEWABLE" ||
    value === "SOLVED" ||
    value === "DISMISSED" ||
    value === "UNSOLVED" ||
    value === "DELETED"
  );
}

function isMessageEnvelope(value: unknown): value is MessageEnvelope<string, Record<string, unknown>> {
  return (
    isRecord(value) &&
    value.schemaVersion === 1 &&
    isNonBlankString(value.type) &&
    isNonBlankString(value.requestId) &&
    isSourceSurface(value.source) &&
    isRecord(value.payload)
  );
}

function isSourceSurface(value: unknown): boolean {
  return value === "app" || value === "background" || value === "onboarding" || value === "popup" || value === "sidepanel";
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
