import {
  PROBLEM_SESSION_MESSAGE_TYPES,
  parseProblemSessionRequestMessage,
  parseProblemSessionResponseMessage,
  problemSessionResponse
} from "../src/problem-session";
import { describe, expect, it } from "vitest";

describe("problem session extension messages", () => {
  it("parses bounded request messages", () => {
    expect(
      parseProblemSessionRequestMessage({
        schemaVersion: 1,
        type: PROBLEM_SESSION_MESSAGE_TYPES.start,
        requestId: "request-1",
        source: "popup",
        payload: { title: "JWT authentication failing" }
      })
    ).toMatchObject({
      message: {
        payload: { title: "JWT authentication failing" }
      },
      ok: true
    });
  });

  it("rejects manual starts without a problem title", () => {
    expect(
      parseProblemSessionRequestMessage({
        schemaVersion: 1,
        type: PROBLEM_SESSION_MESSAGE_TYPES.start,
        requestId: "request-1",
        source: "popup",
        payload: {}
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });
  });

  it("rejects unbounded request payloads", () => {
    expect(
      parseProblemSessionRequestMessage({
        schemaVersion: 1,
        type: PROBLEM_SESSION_MESSAGE_TYPES.finish,
        requestId: "request-1",
        source: "popup",
        payload: { accessToken: "must-not-cross-ui-boundary" }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });
  });

  it("parses bounded solution-save requests", () => {
    expect(
      parseProblemSessionRequestMessage({
        schemaVersion: 1,
        type: PROBLEM_SESSION_MESSAGE_TYPES.saveSolution,
        requestId: "request-2",
        source: "sidepanel",
        payload: { finalSolution: "Allowed localhost:3000 in CorsConfiguration." }
      })
    ).toMatchObject({
      message: {
        payload: { finalSolution: "Allowed localhost:3000 in CorsConfiguration." }
      },
      ok: true
    });
  });

  it("round-trips a safe runtime response without raw activity", () => {
    const request = {
      schemaVersion: 1,
      type: PROBLEM_SESSION_MESSAGE_TYPES.get,
      requestId: "request-1",
      source: "popup",
      payload: {}
    } as const;

    const response = problemSessionResponse(request, {
      commandStatus: "received",
      state: {
        activeSession: {
          detectionReasons: ["manual_session"],
          endedAt: null,
          etag: "\"0\"",
          id: "10000000-0000-4000-8000-000000000001",
          source: "MANUAL",
          startedAt: "2026-07-16T08:00:00.000Z",
          status: "CANDIDATE",
          title: "Technical problem",
          updatedAt: "2026-07-16T08:00:00.000Z",
          version: 0
        },
        dismissedSession: null
      }
    });

    expect(parseProblemSessionResponseMessage(response)).toMatchObject({ ok: true });
    expect(JSON.stringify(response)).not.toContain("https://");
    expect(JSON.stringify(response)).not.toContain("access-token");
  });
});
