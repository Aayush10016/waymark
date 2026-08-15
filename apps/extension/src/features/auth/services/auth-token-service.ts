import { AUTH_STATUS } from "@trail/extension-messages";
import type { AuthRefreshClient } from "../api/auth-refresh-client";
import {
  isUsableInstant,
  toMemoryAccessToken,
  toStoredRefreshSession,
  type AuthState,
  type IssuedAuthTokens,
  type MemoryAccessToken
} from "../model/auth-session";
import type { AuthSessionStorage } from "../../../storage/auth/auth-session-storage";

export interface AuthTokenServiceOptions {
  readonly refreshClient: AuthRefreshClient;
  readonly storage: AuthSessionStorage;
  readonly now: () => Date;
}

export class AuthTokenService {
  private readonly refreshClient: AuthRefreshClient;
  private readonly storage: AuthSessionStorage;
  private readonly now: () => Date;
  private accessToken: MemoryAccessToken | null = null;
  private pendingRefresh: Promise<string | null> | null = null;

  constructor(options: AuthTokenServiceOptions) {
    this.refreshClient = options.refreshClient;
    this.storage = options.storage;
    this.now = options.now;
  }

  async establishSession(tokens: IssuedAuthTokens): Promise<AuthState> {
    const updatedAt = this.now().toISOString();
    this.accessToken = toMemoryAccessToken(tokens);
    await this.storage.write(toStoredRefreshSession(tokens, updatedAt));
    return this.status();
  }

  async status(): Promise<AuthState> {
    const nowMs = this.now().getTime();
    const storedRefresh = await this.storage.read();
    if (this.accessToken !== null && isUsableInstant(this.accessToken.expiresAt, nowMs)) {
      return {
        status: AUTH_STATUS.authenticated,
        accessTokenExpiresAt: this.accessToken.expiresAt,
        refreshTokenExpiresAt: storedRefresh?.refreshTokenExpiresAt ?? this.accessToken.expiresAt
      };
    }

    if (storedRefresh !== null && isUsableInstant(storedRefresh.refreshTokenExpiresAt, nowMs)) {
      const refreshedAccessToken = await this.refreshAccessToken();
      if (refreshedAccessToken !== null && this.accessToken !== null) {
        const rotatedRefresh = await this.storage.read();
        return {
          status: AUTH_STATUS.authenticated,
          accessTokenExpiresAt: this.accessToken.expiresAt,
          refreshTokenExpiresAt: rotatedRefresh?.refreshTokenExpiresAt ?? this.accessToken.expiresAt
        };
      }

      return { status: AUTH_STATUS.signedOut };
    }

    this.accessToken = null;
    if (storedRefresh !== null) {
      await this.storage.clear();
    }
    return { status: AUTH_STATUS.signedOut };
  }

  async getValidAccessToken(): Promise<string | null> {
    const nowMs = this.now().getTime();
    if (this.accessToken !== null && isUsableInstant(this.accessToken.expiresAt, nowMs)) {
      return this.accessToken.token;
    }

    if (this.pendingRefresh !== null) {
      return this.pendingRefresh;
    }

    this.pendingRefresh = this.refreshAccessToken();
    try {
      return await this.pendingRefresh;
    } finally {
      this.pendingRefresh = null;
    }
  }

  async clearSession(): Promise<void> {
    this.accessToken = null;
    await this.storage.clear();
  }

  private async refreshAccessToken(): Promise<string | null> {
    const storedRefresh = await this.storage.read();
    const nowMs = this.now().getTime();
    if (storedRefresh === null || !isUsableInstant(storedRefresh.refreshTokenExpiresAt, nowMs)) {
      this.accessToken = null;
      if (storedRefresh !== null) {
        await this.storage.clear();
      }
      return null;
    }

    const result = await this.refreshClient.refresh(storedRefresh.refreshToken);
    if (result.status !== "ROTATED") {
      this.accessToken = null;
      await this.storage.clear();
      return null;
    }

    this.accessToken = toMemoryAccessToken(result.tokens);
    await this.storage.write(toStoredRefreshSession(result.tokens, this.now().toISOString()));
    return this.accessToken.token;
  }
}
