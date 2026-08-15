export const AUTH_MESSAGE_TYPES = {
  clearSession: "AUTH_SESSION_CLEAR",
  establishSession: "AUTH_SESSION_ESTABLISH",
  getStatus: "AUTH_STATUS_GET"
} as const;

export const AUTH_RESPONSE_TYPES = {
  clearSession: "AUTH_SESSION_CLEAR_RESULT",
  establishSession: "AUTH_SESSION_ESTABLISH_RESULT",
  getStatus: "AUTH_STATUS_RESULT"
} as const;

export const AUTH_STATUS = {
  authenticated: "AUTHENTICATED",
  refreshRequired: "REFRESH_REQUIRED",
  signedOut: "SIGNED_OUT"
} as const;

export type AuthMessageType = (typeof AUTH_MESSAGE_TYPES)[keyof typeof AUTH_MESSAGE_TYPES];
export type AuthResponseType = (typeof AUTH_RESPONSE_TYPES)[keyof typeof AUTH_RESPONSE_TYPES];
export type AuthStatus = (typeof AUTH_STATUS)[keyof typeof AUTH_STATUS];

export type SourceSurface = "app" | "background" | "onboarding" | "popup" | "sidepanel";

export interface MessageEnvelope<Type extends string, Payload> {
  readonly schemaVersion: 1;
  readonly type: Type;
  readonly requestId: string;
  readonly source: SourceSurface;
  readonly payload: Payload;
}

export interface AuthSessionTokensPayload {
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
  readonly refreshToken: string;
  readonly refreshTokenExpiresAt: string;
}

export type AuthStatusPayload =
  | {
      readonly status: typeof AUTH_STATUS.authenticated;
      readonly accessTokenExpiresAt: string;
      readonly refreshTokenExpiresAt: string;
    }
  | {
      readonly status: typeof AUTH_STATUS.refreshRequired;
      readonly refreshTokenExpiresAt: string;
    }
  | {
      readonly status: typeof AUTH_STATUS.signedOut;
    };

export type AuthEstablishSessionMessage = MessageEnvelope<
  typeof AUTH_MESSAGE_TYPES.establishSession,
  AuthSessionTokensPayload
>;

export type AuthGetStatusMessage = MessageEnvelope<typeof AUTH_MESSAGE_TYPES.getStatus, Record<string, never>>;

export type AuthClearSessionMessage = MessageEnvelope<typeof AUTH_MESSAGE_TYPES.clearSession, Record<string, never>>;

export type AuthRequestMessage = AuthClearSessionMessage | AuthEstablishSessionMessage | AuthGetStatusMessage;

export type AuthStatusResponseMessage = MessageEnvelope<typeof AUTH_RESPONSE_TYPES.getStatus, AuthStatusPayload>;

export type AuthEstablishSessionResponseMessage = MessageEnvelope<
  typeof AUTH_RESPONSE_TYPES.establishSession,
  AuthStatusPayload
>;

export type AuthClearSessionResponseMessage = MessageEnvelope<
  typeof AUTH_RESPONSE_TYPES.clearSession,
  { readonly status: typeof AUTH_STATUS.signedOut }
>;

export type AuthResponseMessage =
  | AuthClearSessionResponseMessage
  | AuthEstablishSessionResponseMessage
  | AuthStatusResponseMessage;

export type AuthMessageParseResult =
  | { readonly ok: true; readonly message: AuthRequestMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export function parseAuthRequestMessage(input: unknown): AuthMessageParseResult {
  if (!isRecord(input)) {
    return invalidMessage();
  }

  if (
    input.schemaVersion !== 1 ||
    !isAuthRequestType(input.type) ||
    !isNonBlankString(input.requestId) ||
    !isSourceSurface(input.source) ||
    !isRecord(input.payload)
  ) {
    return invalidMessage();
  }

  if (input.type === AUTH_MESSAGE_TYPES.establishSession) {
    if (!isAuthSessionTokensPayload(input.payload)) {
      return invalidMessage();
    }
    return {
      ok: true,
      message: {
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.establishSession,
        requestId: input.requestId,
        source: input.source,
        payload: input.payload
      }
    };
  }

  if (Object.keys(input.payload).length !== 0) {
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
    }
  };
}

export function authStatusResponse(
  request: AuthGetStatusMessage,
  payload: AuthStatusPayload
): AuthStatusResponseMessage {
  return {
    schemaVersion: 1,
    type: AUTH_RESPONSE_TYPES.getStatus,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

export function authEstablishSessionResponse(
  request: AuthEstablishSessionMessage,
  payload: AuthStatusPayload
): AuthEstablishSessionResponseMessage {
  return {
    schemaVersion: 1,
    type: AUTH_RESPONSE_TYPES.establishSession,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

export function authClearSessionResponse(request: AuthClearSessionMessage): AuthClearSessionResponseMessage {
  return {
    schemaVersion: 1,
    type: AUTH_RESPONSE_TYPES.clearSession,
    requestId: request.requestId,
    source: "background",
    payload: { status: AUTH_STATUS.signedOut }
  };
}

function invalidMessage(): AuthMessageParseResult {
  return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
}

function isAuthRequestType(value: unknown): value is AuthMessageType {
  return (
    value === AUTH_MESSAGE_TYPES.clearSession ||
    value === AUTH_MESSAGE_TYPES.establishSession ||
    value === AUTH_MESSAGE_TYPES.getStatus
  );
}

function isAuthSessionTokensPayload(value: unknown): value is AuthSessionTokensPayload {
  if (!isRecord(value)) {
    return false;
  }

  return (
    isNonBlankString(value.accessToken) &&
    isRfc3339Instant(value.accessTokenExpiresAt) &&
    isNonBlankString(value.refreshToken) &&
    isRfc3339Instant(value.refreshTokenExpiresAt) &&
    Object.keys(value).length === 4
  );
}

function isSourceSurface(value: unknown): value is SourceSurface {
  return (
    value === "app" ||
    value === "background" ||
    value === "onboarding" ||
    value === "popup" ||
    value === "sidepanel"
  );
}

function isRfc3339Instant(value: unknown): value is string {
  return isNonBlankString(value) && !Number.isNaN(Date.parse(value));
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
