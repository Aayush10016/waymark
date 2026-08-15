import { AUTH_STATUS, type AuthStatus, type MessageEnvelope } from "./auth";

export const CAPTURE_STATE_MESSAGE_TYPES = {
  excludeCurrentDomain: "CAPTURE_STATE_EXCLUDE_CURRENT_DOMAIN",
  get: "CAPTURE_STATE_GET",
  pause: "CAPTURE_STATE_PAUSE",
  resume: "CAPTURE_STATE_RESUME",
  undoDomainExclusion: "CAPTURE_STATE_UNDO_DOMAIN_EXCLUSION"
} as const;

export const CAPTURE_STATE_RESPONSE_TYPES = {
  get: "CAPTURE_STATE_RESULT"
} as const;

export type CaptureStateMessageType = (typeof CAPTURE_STATE_MESSAGE_TYPES)[keyof typeof CAPTURE_STATE_MESSAGE_TYPES];
export type CaptureStateResponseType =
  (typeof CAPTURE_STATE_RESPONSE_TYPES)[keyof typeof CAPTURE_STATE_RESPONSE_TYPES];

export type ConsentStatus = "accepted" | "declined" | "missing" | "stale";
export type CapturePermissionStatus = "granted" | "missing";
export type CaptureSyncStatus = "idle" | "offline";
export type PopupPriorityState =
  | "auth_required"
  | "consent_required"
  | "permission_missing"
  | "domain_excluded"
  | "paused"
  | "offline"
  | "recording_ready";

export interface CaptureStateQueuePayload {
  readonly maxBatchSize: number;
  readonly maxRecords: number;
  readonly oldestEnqueuedAt: string | null;
  readonly remainingCapacity: number;
  readonly totalRecords: number;
}

export interface CaptureStatePayload {
  readonly authStatus: AuthStatus;
  readonly consentStatus: ConsentStatus;
  readonly controls: {
    readonly currentDomain: string | null;
    readonly currentDomainExcluded: boolean;
    readonly excludedDomainCount: number;
    readonly paused: boolean;
  };
  readonly network: {
    readonly online: boolean;
  };
  readonly permissions: {
    readonly missing: readonly string[];
    readonly status: CapturePermissionStatus;
  };
  readonly popup: {
    readonly primaryAction: "open_onboarding" | "restore_permission" | "resume_capture" | "open_trail";
    readonly state: PopupPriorityState;
    readonly statusLabel: string;
  };
  readonly queue: CaptureStateQueuePayload;
  readonly sync: {
    readonly status: CaptureSyncStatus;
  };
}

export type CaptureStateGetMessage = MessageEnvelope<
  typeof CAPTURE_STATE_MESSAGE_TYPES.get,
  Record<string, never>
>;
export type CaptureStatePauseMessage = MessageEnvelope<
  typeof CAPTURE_STATE_MESSAGE_TYPES.pause,
  Record<string, never>
>;
export type CaptureStateResumeMessage = MessageEnvelope<
  typeof CAPTURE_STATE_MESSAGE_TYPES.resume,
  Record<string, never>
>;
export type CaptureStateExcludeCurrentDomainMessage = MessageEnvelope<
  typeof CAPTURE_STATE_MESSAGE_TYPES.excludeCurrentDomain,
  Record<string, never>
>;
export type CaptureStateUndoDomainExclusionMessage = MessageEnvelope<
  typeof CAPTURE_STATE_MESSAGE_TYPES.undoDomainExclusion,
  Record<string, never>
>;

export type CaptureStateRequestMessage =
  | CaptureStateExcludeCurrentDomainMessage
  | CaptureStateGetMessage
  | CaptureStatePauseMessage
  | CaptureStateResumeMessage
  | CaptureStateUndoDomainExclusionMessage;

export type CaptureStateResponseMessage = MessageEnvelope<
  typeof CAPTURE_STATE_RESPONSE_TYPES.get,
  CaptureStatePayload
>;

export type CaptureStateMessageParseResult =
  | { readonly ok: true; readonly message: CaptureStateRequestMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export type CaptureStateResponseParseResult =
  | { readonly ok: true; readonly message: CaptureStateResponseMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export function parseCaptureStateRequestMessage(input: unknown): CaptureStateMessageParseResult {
  if (!isMessageEnvelope(input) || !isCaptureStateRequestType(input.type) || Object.keys(input.payload).length !== 0) {
    return invalidMessage();
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: input.type,
      requestId: input.requestId,
      source: input.source,
      payload: {}
    } as CaptureStateRequestMessage
  };
}

export function parseCaptureStateResponseMessage(input: unknown): CaptureStateResponseParseResult {
  if (
    !isMessageEnvelope(input) ||
    input.type !== CAPTURE_STATE_RESPONSE_TYPES.get ||
    !isCaptureStatePayload(input.payload)
  ) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: CAPTURE_STATE_RESPONSE_TYPES.get,
      requestId: input.requestId,
      source: input.source,
      payload: input.payload
    }
  };
}

export function captureStateResponse(
  request: CaptureStateRequestMessage,
  payload: CaptureStatePayload
): CaptureStateResponseMessage {
  return {
    schemaVersion: 1,
    type: CAPTURE_STATE_RESPONSE_TYPES.get,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

function invalidMessage(): CaptureStateMessageParseResult {
  return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
}

function isMessageEnvelope(value: unknown): value is MessageEnvelope<string, Record<string, unknown>> {
  if (!isRecord(value)) {
    return false;
  }

  return (
    value.schemaVersion === 1 &&
    isNonBlankString(value.type) &&
    isNonBlankString(value.requestId) &&
    isSourceSurface(value.source) &&
    isRecord(value.payload)
  );
}

function isCaptureStateRequestType(value: unknown): value is CaptureStateMessageType {
  return (
    value === CAPTURE_STATE_MESSAGE_TYPES.excludeCurrentDomain ||
    value === CAPTURE_STATE_MESSAGE_TYPES.get ||
    value === CAPTURE_STATE_MESSAGE_TYPES.pause ||
    value === CAPTURE_STATE_MESSAGE_TYPES.resume ||
    value === CAPTURE_STATE_MESSAGE_TYPES.undoDomainExclusion
  );
}

function isCaptureStatePayload(value: unknown): value is CaptureStatePayload {
  if (!isRecord(value)) {
    return false;
  }

  return (
    isAuthStatus(value.authStatus) &&
    isConsentStatus(value.consentStatus) &&
    isControlsPayload(value.controls) &&
    isNetworkPayload(value.network) &&
    isPermissionsPayload(value.permissions) &&
    isPopupPayload(value.popup) &&
    isQueuePayload(value.queue) &&
    isSyncPayload(value.sync) &&
    Object.keys(value).length === 8
  );
}

function isControlsPayload(value: unknown): value is CaptureStatePayload["controls"] {
  return (
    isRecord(value) &&
    (typeof value.currentDomain === "string" || value.currentDomain === null) &&
    typeof value.currentDomainExcluded === "boolean" &&
    typeof value.excludedDomainCount === "number" &&
    Number.isInteger(value.excludedDomainCount) &&
    value.excludedDomainCount >= 0 &&
    typeof value.paused === "boolean" &&
    Object.keys(value).length === 4
  );
}

function isNetworkPayload(value: unknown): value is CaptureStatePayload["network"] {
  return isRecord(value) && typeof value.online === "boolean" && Object.keys(value).length === 1;
}

function isPermissionsPayload(value: unknown): value is CaptureStatePayload["permissions"] {
  return (
    isRecord(value) &&
    (value.status === "granted" || value.status === "missing") &&
    Array.isArray(value.missing) &&
    value.missing.every((permission) => typeof permission === "string") &&
    Object.keys(value).length === 2
  );
}

function isPopupPayload(value: unknown): value is CaptureStatePayload["popup"] {
  return (
    isRecord(value) &&
    isPopupPriorityState(value.state) &&
    isNonBlankString(value.statusLabel) &&
    isPrimaryAction(value.primaryAction) &&
    Object.keys(value).length === 3
  );
}

function isQueuePayload(value: unknown): value is CaptureStateQueuePayload {
  return (
    isRecord(value) &&
    typeof value.maxBatchSize === "number" &&
    Number.isInteger(value.maxBatchSize) &&
    typeof value.maxRecords === "number" &&
    Number.isInteger(value.maxRecords) &&
    (typeof value.oldestEnqueuedAt === "string" || value.oldestEnqueuedAt === null) &&
    typeof value.remainingCapacity === "number" &&
    Number.isInteger(value.remainingCapacity) &&
    typeof value.totalRecords === "number" &&
    Number.isInteger(value.totalRecords) &&
    Object.keys(value).length === 5
  );
}

function isSyncPayload(value: unknown): value is CaptureStatePayload["sync"] {
  return isRecord(value) && (value.status === "idle" || value.status === "offline") && Object.keys(value).length === 1;
}

function isAuthStatus(value: unknown): value is AuthStatus {
  return value === AUTH_STATUS.authenticated || value === AUTH_STATUS.refreshRequired || value === AUTH_STATUS.signedOut;
}

function isConsentStatus(value: unknown): value is ConsentStatus {
  return value === "accepted" || value === "declined" || value === "missing" || value === "stale";
}

function isPopupPriorityState(value: unknown): value is PopupPriorityState {
  return (
    value === "auth_required" ||
    value === "consent_required" ||
    value === "permission_missing" ||
    value === "domain_excluded" ||
    value === "paused" ||
    value === "offline" ||
    value === "recording_ready"
  );
}

function isPrimaryAction(value: unknown): value is CaptureStatePayload["popup"]["primaryAction"] {
  return value === "open_onboarding" || value === "restore_permission" || value === "resume_capture" || value === "open_trail";
}

function isSourceSurface(value: unknown): boolean {
  return value === "app" || value === "background" || value === "onboarding" || value === "popup" || value === "sidepanel";
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
