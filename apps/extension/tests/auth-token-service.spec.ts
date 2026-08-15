import { AUTH_MESSAGE_TYPES, AUTH_RESPONSE_TYPES, AUTH_STATUS } from "@trail/extension-messages";
import { describe, expect, it } from "vitest";
import { type AuthRefreshClient, AuthTokenService, handleAuthMessage } from "../src/features/auth";
import type { IssuedAuthTokens, StoredRefreshSession } from "../src/features/auth/model/auth-session";
import type { AuthSessionStorage } from "../src/storage/auth/auth-session-storage";

const NOW = new Date("2026-07-11T08:00:00.000Z");
const ACCESS_EXPIRES_AT = "2026-07-11T08:10:00.000Z";
const REFRESH_EXPIRES_AT = "2026-08-10T08:00:00.000Z";
const EXPIRED_ACCESS_AT = "2026-07-11T07:59:00.000Z";
const EXPIRED_REFRESH_AT = "2026-07-11T07:59:00.000Z";

describe("AuthTokenService", () => {
  it("keeps access token memory-only and stores minimized refresh metadata", async () => {
    const storage = new InMemoryAuthSessionStorage();
    const service = new AuthTokenService({
      refreshClient: new SequencedRefreshClient(),
      storage,
      now: () => NOW
    });

    await service.establishSession({
      accessToken: "access-token",
      accessTokenExpiresAt: ACCESS_EXPIRES_AT,
      refreshToken: "refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });

    expect(await service.getValidAccessToken()).toBe("access-token");
    expect(storage.snapshot()).toEqual({
      schemaVersion: 1,
      refreshToken: "refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT,
      updatedAt: NOW.toISOString()
    });
    expect(JSON.stringify(storage.snapshot())).not.toContain("access-token");
  });

  it("rotates refresh credentials when the memory access token expires", async () => {
    const storage = new InMemoryAuthSessionStorage();
    const refreshClient = new SequencedRefreshClient({
      accessToken: "rotated-access-token",
      accessTokenExpiresAt: ACCESS_EXPIRES_AT,
      refreshToken: "rotated-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });
    const service = new AuthTokenService({
      refreshClient,
      storage,
      now: () => NOW
    });
    await service.establishSession({
      accessToken: "expired-access-token",
      accessTokenExpiresAt: EXPIRED_ACCESS_AT,
      refreshToken: "initial-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });

    await expect(service.getValidAccessToken()).resolves.toBe("rotated-access-token");

    expect(refreshClient.refreshTokens()).toEqual(["initial-refresh-token"]);
    expect(storage.snapshot()?.refreshToken).toBe("rotated-refresh-token");
    expect(JSON.stringify(storage.snapshot())).not.toContain("rotated-access-token");
  });

  it("restores authentication from stored refresh credentials after service-worker reconstruction", async () => {
    const storage = new InMemoryAuthSessionStorage();
    await storage.write({
      schemaVersion: 1,
      refreshToken: "persisted-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT,
      updatedAt: NOW.toISOString()
    });
    const refreshClient = new SequencedRefreshClient({
      accessToken: "restored-access-token",
      accessTokenExpiresAt: ACCESS_EXPIRES_AT,
      refreshToken: "rotated-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });
    const service = new AuthTokenService({
      refreshClient,
      storage,
      now: () => NOW
    });

    await expect(service.status()).resolves.toEqual({
      status: AUTH_STATUS.authenticated,
      accessTokenExpiresAt: ACCESS_EXPIRES_AT,
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });
    await expect(service.getValidAccessToken()).resolves.toBe("restored-access-token");
    expect(refreshClient.refreshTokens()).toEqual(["persisted-refresh-token"]);
    expect(storage.snapshot()?.refreshToken).toBe("rotated-refresh-token");
    expect(JSON.stringify(storage.snapshot())).not.toContain("restored-access-token");
  });

  it("deduplicates concurrent refresh attempts", async () => {
    const storage = new InMemoryAuthSessionStorage();
    const refreshClient = new SequencedRefreshClient({
      accessToken: "rotated-access-token",
      accessTokenExpiresAt: ACCESS_EXPIRES_AT,
      refreshToken: "rotated-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });
    const service = new AuthTokenService({
      refreshClient,
      storage,
      now: () => NOW
    });
    await service.establishSession({
      accessToken: "expired-access-token",
      accessTokenExpiresAt: EXPIRED_ACCESS_AT,
      refreshToken: "initial-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });

    const [first, second] = await Promise.all([service.getValidAccessToken(), service.getValidAccessToken()]);

    expect(first).toBe("rotated-access-token");
    expect(second).toBe("rotated-access-token");
    expect(refreshClient.refreshTokens()).toEqual(["initial-refresh-token"]);
  });

  it("clears only auth storage after rejected refresh so drafts remain intact", async () => {
    const storage = new InMemoryAuthSessionStorage();
    storage.setDraft("draft-problem-1", "Allowed localhost:3000 in CORS config.");
    const service = new AuthTokenService({
      refreshClient: new SequencedRefreshClient("REJECTED"),
      storage,
      now: () => NOW
    });
    await service.establishSession({
      accessToken: "expired-access-token",
      accessTokenExpiresAt: EXPIRED_ACCESS_AT,
      refreshToken: "initial-refresh-token",
      refreshTokenExpiresAt: REFRESH_EXPIRES_AT
    });

    await expect(service.getValidAccessToken()).resolves.toBeNull();

    expect(storage.snapshot()).toBeNull();
    expect(storage.draft("draft-problem-1")).toBe("Allowed localhost:3000 in CORS config.");
  });

  it("treats expired refresh metadata as signed out", async () => {
    const storage = new InMemoryAuthSessionStorage();
    const service = new AuthTokenService({
      refreshClient: new SequencedRefreshClient(),
      storage,
      now: () => NOW
    });
    await service.establishSession({
      accessToken: "expired-access-token",
      accessTokenExpiresAt: EXPIRED_ACCESS_AT,
      refreshToken: "expired-refresh-token",
      refreshTokenExpiresAt: EXPIRED_REFRESH_AT
    });

    await expect(service.status()).resolves.toEqual({ status: AUTH_STATUS.signedOut });
    expect(storage.snapshot()).toBeNull();
  });
});

describe("auth message handler", () => {
  it("returns bounded auth status without exposing token values", async () => {
    const storage = new InMemoryAuthSessionStorage();
    const service = new AuthTokenService({
      refreshClient: new SequencedRefreshClient(),
      storage,
      now: () => NOW
    });

    const response = await handleAuthMessage(service, {
      schemaVersion: 1,
      type: AUTH_MESSAGE_TYPES.establishSession,
      requestId: "request-1",
      source: "onboarding",
      payload: {
        accessToken: "access-token",
        accessTokenExpiresAt: ACCESS_EXPIRES_AT,
        refreshToken: "refresh-token",
        refreshTokenExpiresAt: REFRESH_EXPIRES_AT
      }
    });

    expect(response).toEqual({
      handled: true,
      response: {
        schemaVersion: 1,
        type: AUTH_RESPONSE_TYPES.establishSession,
        requestId: "request-1",
        source: "background",
        payload: {
          status: AUTH_STATUS.authenticated,
          accessTokenExpiresAt: ACCESS_EXPIRES_AT,
          refreshTokenExpiresAt: REFRESH_EXPIRES_AT
        }
      }
    });
    expect(JSON.stringify(response)).not.toContain("access-token");
    expect(JSON.stringify(response)).not.toContain("refresh-token");
  });

  it("rejects invalid auth message schemas", async () => {
    const service = new AuthTokenService({
      refreshClient: new SequencedRefreshClient(),
      storage: new InMemoryAuthSessionStorage(),
      now: () => NOW
    });

    await expect(
      handleAuthMessage(service, {
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.getStatus,
        requestId: "request-1",
        source: "popup",
        payload: { accessToken: "must-not-be-here" }
      })
    ).resolves.toEqual({
      handled: false,
      response: { errorCode: "INVALID_MESSAGE_SCHEMA" }
    });
  });
});

class InMemoryAuthSessionStorage implements AuthSessionStorage {
  private storedSession: StoredRefreshSession | null = null;
  private readonly drafts = new Map<string, string>();

  read(): Promise<StoredRefreshSession | null> {
    return Promise.resolve(this.storedSession);
  }

  write(session: StoredRefreshSession): Promise<void> {
    this.storedSession = session;
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.storedSession = null;
    return Promise.resolve();
  }

  snapshot(): StoredRefreshSession | null {
    return this.storedSession;
  }

  setDraft(id: string, value: string): void {
    this.drafts.set(id, value);
  }

  draft(id: string): string | undefined {
    return this.drafts.get(id);
  }
}

class SequencedRefreshClient implements AuthRefreshClient {
  private readonly results: Array<IssuedAuthTokens | "REJECTED">;
  private readonly tokens: string[] = [];

  constructor(...results: Array<IssuedAuthTokens | "REJECTED">) {
    this.results = results;
  }

  refresh(refreshToken: string): Promise<Awaited<ReturnType<AuthRefreshClient["refresh"]>>> {
    this.tokens.push(refreshToken);
    const nextResult = this.results.shift();
    if (nextResult === undefined || nextResult === "REJECTED") {
      return Promise.resolve({ status: "REJECTED" });
    }
    return Promise.resolve({ status: "ROTATED", tokens: nextResult });
  }

  refreshTokens(): string[] {
    return [...this.tokens];
  }
}
