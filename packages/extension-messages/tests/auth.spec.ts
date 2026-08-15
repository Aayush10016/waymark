import {
  AUTH_MESSAGE_TYPES,
  AUTH_RESPONSE_TYPES,
  AUTH_STATUS,
  authClearSessionResponse,
  authEstablishSessionResponse,
  authStatusResponse,
  parseAuthRequestMessage,
  type AuthClearSessionMessage,
  type AuthEstablishSessionMessage,
  type AuthGetStatusMessage
} from "../src";
import { describe, expect, it } from "vitest";

describe("auth extension messages", () => {
  it("accepts a valid establish-session message", () => {
    const result = parseAuthRequestMessage({
      schemaVersion: 1,
      type: AUTH_MESSAGE_TYPES.establishSession,
      requestId: "request-1",
      source: "onboarding",
      payload: {
        accessToken: "access-token",
        accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
        refreshToken: "refresh-token",
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      }
    });

    expect(result).toEqual({
      ok: true,
      message: {
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.establishSession,
        requestId: "request-1",
        source: "onboarding",
        payload: {
          accessToken: "access-token",
          accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
          refreshToken: "refresh-token",
          refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
        }
      }
    });
  });

  it("rejects unknown versions, token leakage fields, and malformed dates", () => {
    expect(
      parseAuthRequestMessage({
        schemaVersion: 2,
        type: AUTH_MESSAGE_TYPES.getStatus,
        requestId: "request-1",
        source: "popup",
        payload: {}
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseAuthRequestMessage({
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.getStatus,
        requestId: "request-1",
        source: "popup",
        payload: { accessToken: "must-not-be-here" }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseAuthRequestMessage({
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.establishSession,
        requestId: "request-1",
        source: "onboarding",
        payload: {
          accessToken: "access-token",
          accessTokenExpiresAt: "not-a-date",
          refreshToken: "refresh-token",
          refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
        }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });
  });

  it("creates bounded auth response messages without access token values", () => {
    const statusRequest: AuthGetStatusMessage = {
      schemaVersion: 1,
      type: AUTH_MESSAGE_TYPES.getStatus,
      requestId: "status-1",
      source: "popup",
      payload: {}
    };
    const establishRequest: AuthEstablishSessionMessage = {
      schemaVersion: 1,
      type: AUTH_MESSAGE_TYPES.establishSession,
      requestId: "establish-1",
      source: "onboarding",
      payload: {
        accessToken: "access-token",
        accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
        refreshToken: "refresh-token",
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      }
    };
    const clearRequest: AuthClearSessionMessage = {
      schemaVersion: 1,
      type: AUTH_MESSAGE_TYPES.clearSession,
      requestId: "clear-1",
      source: "app",
      payload: {}
    };

    expect(
      authStatusResponse(statusRequest, {
        status: AUTH_STATUS.refreshRequired,
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      })
    ).toEqual({
      schemaVersion: 1,
      type: AUTH_RESPONSE_TYPES.getStatus,
      requestId: "status-1",
      source: "background",
      payload: {
        status: AUTH_STATUS.refreshRequired,
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      }
    });

    expect(
      authEstablishSessionResponse(establishRequest, {
        status: AUTH_STATUS.authenticated,
        accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      })
    ).toEqual({
      schemaVersion: 1,
      type: AUTH_RESPONSE_TYPES.establishSession,
      requestId: "establish-1",
      source: "background",
      payload: {
        status: AUTH_STATUS.authenticated,
        accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
        refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
      }
    });
    expect(
      JSON.stringify(
        authEstablishSessionResponse(establishRequest, {
          status: AUTH_STATUS.authenticated,
          accessTokenExpiresAt: "2026-07-11T08:00:00.000Z",
          refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
        })
      )
    ).not.toContain("access-token");

    expect(authClearSessionResponse(clearRequest)).toEqual({
      schemaVersion: 1,
      type: AUTH_RESPONSE_TYPES.clearSession,
      requestId: "clear-1",
      source: "background",
      payload: { status: AUTH_STATUS.signedOut }
    });
  });
});
