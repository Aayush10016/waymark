import { describe, expect, it } from "vitest";
import { SyncingCaptureEventSink, type CaptureQueueSynchronizer } from "../src/services/capture-sync";
import type { CaptureEvent, CaptureEventSink } from "../src/services/navigation-capture";

describe("SyncingCaptureEventSink", () => {
  it("persists the capture event before triggering best-effort synchronization", async () => {
    const delegate = new RecordingCaptureEventSink();
    const synchronizer = new RecordingCaptureQueueSynchronizer();
    const sink = new SyncingCaptureEventSink({ delegate, synchronizer });
    const event = captureEvent();

    await sink.emit(event);
    await synchronizer.pendingSynchronization;

    expect(delegate.events).toEqual([event]);
    expect(synchronizer.calls).toBe(1);
  });

  it("does not fail capture persistence when asynchronous synchronization fails", async () => {
    const delegate = new RecordingCaptureEventSink();
    const synchronizer = new RecordingCaptureQueueSynchronizer(Promise.reject(new Error("offline")));
    const sink = new SyncingCaptureEventSink({ delegate, synchronizer });
    const event = captureEvent();

    await expect(sink.emit(event)).resolves.toBeUndefined();
    await synchronizer.pendingSynchronization.catch(() => undefined);

    expect(delegate.events).toEqual([event]);
  });
});

class RecordingCaptureEventSink implements CaptureEventSink {
  readonly events: CaptureEvent[] = [];

  emit(event: CaptureEvent): Promise<void> {
    this.events.push(event);
    return Promise.resolve();
  }
}

class RecordingCaptureQueueSynchronizer implements CaptureQueueSynchronizer {
  calls = 0;
  pendingSynchronization: Promise<unknown> = Promise.resolve();

  constructor(private readonly result: Promise<unknown> = Promise.resolve()) {}

  synchronize(): Promise<unknown> {
    this.calls += 1;
    this.pendingSynchronization = this.result;
    return this.result;
  }
}

function captureEvent(): CaptureEvent {
  return Object.freeze({
    browserEventTime: null,
    canonicalUrl: "https://spring.io/projects/spring-security",
    capturedAt: "2026-07-31T08:05:00.000Z",
    hostname: "spring.io",
    navigationType: null,
    openerTabId: null,
    policyVersion: "url-query-sanitizer-v1",
    problemSessionId: "77000000-0000-4000-8000-000000000020",
    removedQueryParameters: Object.freeze([]),
    resourceType: "documentation",
    searchEngine: null,
    searchQuery: null,
    source: "tab_updated",
    tabId: 1,
    title: "Spring Security",
    transitionQualifiers: Object.freeze([]),
    windowId: 1
  });
}
