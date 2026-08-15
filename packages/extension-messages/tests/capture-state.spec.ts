import {
  AUTH_STATUS,
  CAPTURE_STATE_MESSAGE_TYPES,
  CAPTURE_STATE_RESPONSE_TYPES,
  captureStateResponse,
  parseCaptureStateRequestMessage,
  parseCaptureStateResponseMessage,
  type CaptureStateGetMessage,
  type CaptureStatePayload
} from "../src";
import { describe, expect, it } from "vitest";

describe("capture-state extension messages", () => {
  it("accepts valid capture-state request messages", () => {
    const result = parseCaptureStateRequestMessage({
      schemaVersion: 1,
      type: CAPTURE_STATE_MESSAGE_TYPES.get,
      requestId: "capture-state-1",
      source: "popup",
      payload: {}
    });

    expect(result).toEqual({
      ok: true,
      message: {
        schemaVersion: 1,
        type: CAPTURE_STATE_MESSAGE_TYPES.get,
        requestId: "capture-state-1",
        source: "popup",
        payload: {}
      }
    });
  });

  it("rejects malformed capture-state messages", () => {
    expect(
      parseCaptureStateRequestMessage({
        schemaVersion: 2,
        type: CAPTURE_STATE_MESSAGE_TYPES.get,
        requestId: "capture-state-1",
        source: "popup",
        payload: {}
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseCaptureStateRequestMessage({
        schemaVersion: 1,
        type: CAPTURE_STATE_MESSAGE_TYPES.get,
        requestId: "capture-state-1",
        source: "popup",
        payload: { tabUrl: "https://example.test/must-not-be-trusted" }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });
  });

  it("creates and validates bounded capture-state responses without raw activity", () => {
    const request: CaptureStateGetMessage = {
      schemaVersion: 1,
      type: CAPTURE_STATE_MESSAGE_TYPES.get,
      requestId: "capture-state-1",
      source: "popup",
      payload: {}
    };

    const response = captureStateResponse(request, captureStatePayload());

    expect(response).toEqual({
      schemaVersion: 1,
      type: CAPTURE_STATE_RESPONSE_TYPES.get,
      requestId: "capture-state-1",
      source: "background",
      payload: captureStatePayload()
    });
    expect(parseCaptureStateResponseMessage(response)).toEqual({ ok: true, message: response });
    expect(JSON.stringify(response)).not.toContain("https://");
    expect(JSON.stringify(response)).not.toContain("Spring Security");
  });
});

function captureStatePayload(): CaptureStatePayload {
  return {
    authStatus: AUTH_STATUS.authenticated,
    consentStatus: "accepted",
    controls: {
      currentDomain: "docs.example.test",
      currentDomainExcluded: false,
      excludedDomainCount: 1,
      paused: false
    },
    network: {
      online: true
    },
    permissions: {
      missing: [],
      status: "granted"
    },
    popup: {
      primaryAction: "open_trail",
      state: "recording_ready",
      statusLabel: "Recording quietly"
    },
    queue: {
      maxBatchSize: 50,
      maxRecords: 1_000,
      oldestEnqueuedAt: null,
      remainingCapacity: 1_000,
      totalRecords: 0
    },
    sync: {
      status: "idle"
    }
  };
}
