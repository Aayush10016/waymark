import { describe, expect, it } from "vitest";
import type { ProblemSessionRuntimePayload } from "@trail/extension-messages";
import {
  ProblemSessionCaptureEventSink,
  type CaptureEvent,
  type CaptureEventSink,
  type CaptureProblemSessionStateReader
} from "../src/services/navigation-capture";

const PROBLEM_SESSION_ID = "10000000-0000-4000-8000-000000000001";

describe("ProblemSessionCaptureEventSink", () => {
  it("associates capture events with the active candidate Problem Session", async () => {
    const delegate = new RecordingCaptureEventSink();
    const sink = new ProblemSessionCaptureEventSink({
      delegate,
      problemSessionReader: new StaticProblemSessionReader({
        activeSession: {
          detectionReasons: ["manual_session"],
          endedAt: null,
          etag: "\"0\"",
          id: PROBLEM_SESSION_ID,
          source: "MANUAL",
          startedAt: "2026-07-13T08:00:00.000Z",
          status: "CANDIDATE",
          title: "Spring CORS",
          updatedAt: "2026-07-13T08:00:00.000Z",
          version: 0
        },
        dismissedSession: null
      })
    });

    await sink.emit(captureEvent());

    expect(delegate.events()).toEqual([
      expect.objectContaining({
        canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
        problemSessionId: PROBLEM_SESSION_ID
      })
    ]);
  });

  it("leaves capture events unassociated when there is no active candidate", async () => {
    const delegate = new RecordingCaptureEventSink();
    const sink = new ProblemSessionCaptureEventSink({
      delegate,
      problemSessionReader: new StaticProblemSessionReader({
        activeSession: null,
        dismissedSession: null
      })
    });

    await sink.emit(captureEvent({ problemSessionId: PROBLEM_SESSION_ID }));

    expect(delegate.events()).toEqual([
      expect.objectContaining({
        problemSessionId: null
      })
    ]);
  });
});

class RecordingCaptureEventSink implements CaptureEventSink {
  private readonly capturedEvents: CaptureEvent[] = [];

  emit(event: CaptureEvent): Promise<void> {
    this.capturedEvents.push(event);
    return Promise.resolve();
  }

  events(): readonly CaptureEvent[] {
    return this.capturedEvents;
  }
}

class StaticProblemSessionReader implements CaptureProblemSessionStateReader {
  constructor(private readonly state: ProblemSessionRuntimePayload) {}

  readState(): Promise<{ readonly state: ProblemSessionRuntimePayload }> {
    return Promise.resolve({ state: this.state });
  }
}

function captureEvent(overrides: Partial<CaptureEvent> = {}): CaptureEvent {
  return Object.freeze({
    browserEventTime: 100,
    canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
    capturedAt: "2026-07-13T08:00:00.000Z",
    hostname: "docs.spring.io",
    navigationType: "link",
    openerTabId: null,
    policyVersion: "url-query-sanitation-v1",
    problemSessionId: null,
    removedQueryParameters: Object.freeze([]),
    resourceType: "documentation",
    searchEngine: null,
    searchQuery: null,
    source: "web_navigation_committed",
    tabId: 9,
    title: "CORS :: Spring Security",
    transitionQualifiers: Object.freeze(["from_address_bar"]),
    windowId: 1,
    ...overrides
  });
}
