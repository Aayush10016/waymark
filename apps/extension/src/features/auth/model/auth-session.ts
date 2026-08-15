import type { AUTH_STATUS, AuthStatusPayload } from "@trail/extension-messages";

export interface IssuedAuthTokens {
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
  readonly refreshToken: string;
  readonly refreshTokenExpiresAt: string;
}

export interface StoredRefreshSession {
  readonly schemaVersion: 1;
  readonly refreshToken: string;
  readonly refreshTokenExpiresAt: string;
  readonly updatedAt: string;
}

export interface MemoryAccessToken {
  readonly token: string;
  readonly expiresAt: string;
}

export type AuthState =
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

export function toAuthStatusPayload(state: AuthState): AuthStatusPayload {
  return state;
}

export function isUsableInstant(value: string, nowMs: number): boolean {
  const parsed = Date.parse(value);
  return !Number.isNaN(parsed) && parsed > nowMs;
}

export function toStoredRefreshSession(tokens: IssuedAuthTokens, updatedAt: string): StoredRefreshSession {
  return {
    schemaVersion: 1,
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt: tokens.refreshTokenExpiresAt,
    updatedAt
  };
}

export function toMemoryAccessToken(tokens: IssuedAuthTokens): MemoryAccessToken {
  return {
    token: tokens.accessToken,
    expiresAt: tokens.accessTokenExpiresAt
  };
}

export function parseStoredRefreshSession(value: unknown): StoredRefreshSession | null {
  if (!isRecord(value)) {
    return null;
  }

  if (
    value.schemaVersion !== 1 ||
    !isNonBlankString(value.refreshToken) ||
    !isRfc3339Instant(value.refreshTokenExpiresAt) ||
    !isRfc3339Instant(value.updatedAt) ||
    Object.keys(value).length !== 4
  ) {
    return null;
  }

  return {
    schemaVersion: 1,
    refreshToken: value.refreshToken,
    refreshTokenExpiresAt: value.refreshTokenExpiresAt,
    updatedAt: value.updatedAt
  };
}

export function parseIssuedAuthTokens(value: unknown): IssuedAuthTokens | null {
  if (!isRecord(value)) {
    return null;
  }

  if (hasIssuedAuthTokens(value)) {
    return toIssuedAuthTokens(value);
  }

  if (isRecord(value.tokens) && hasIssuedAuthTokens(value.tokens)) {
    return toIssuedAuthTokens(value.tokens);
  }

  return null;
}

function hasIssuedAuthTokens(value: Record<string, unknown>): boolean {
  return (
    isNonBlankString(value.accessToken) &&
    isRfc3339Instant(value.accessTokenExpiresAt) &&
    isNonBlankString(value.refreshToken) &&
    isRfc3339Instant(value.refreshTokenExpiresAt)
  );
}

function toIssuedAuthTokens(value: Record<string, unknown>): IssuedAuthTokens {
  return {
    accessToken: String(value.accessToken),
    accessTokenExpiresAt: String(value.accessTokenExpiresAt),
    refreshToken: String(value.refreshToken),
    refreshTokenExpiresAt: String(value.refreshTokenExpiresAt)
  };
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
