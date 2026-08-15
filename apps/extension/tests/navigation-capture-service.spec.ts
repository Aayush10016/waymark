import { describe, expect, it, vi } from "vitest";
import { createUrlQuerySanitizer, defaultCaptureSanitationPolicy } from "../src/services/capture-policy";
import {
  NavigationCaptureService,
  type CaptureConsentReader,
  type CaptureControlReader,
  type CaptureEvent,
  type CaptureEventSink
} from "../src/services/navigation-capture";
import type { CaptureConsentState } from "../src/services/consent";
import type { CaptureControlDecision } from "../src/services/capture-controls";

const CAPTURED_AT = new Date("2026-07-12T11:00:00.000Z");

describe("NavigationCaptureService", () => {
  it("captures sanitized top-level navigation when consent is accepted", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    const result = await service.capture({
      browserEventTime: 12345,
      frameId: 0,
      navigationType: "link",
      openerTabId: 7,
      source: "web_navigation_committed",
      tabId: 42,
      title: " Spring Security CORS  ",
      transitionQualifiers: ["client_redirect"],
      url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?utm_source=test#section",
      windowId: 3
    });

    expect(result.status).toBe("captured");
    expect(sink.events()).toEqual([
      {
        browserEventTime: 12345,
        canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
        capturedAt: CAPTURED_AT.toISOString(),
        hostname: "docs.spring.io",
        navigationType: "link",
        openerTabId: 7,
        policyVersion: "url-query-sanitation-v1",
        problemSessionId: null,
        removedQueryParameters: ["utm_source"],
        resourceType: "documentation",
        searchEngine: null,
        searchQuery: null,
        source: "web_navigation_committed",
        tabId: 42,
        title: "Spring Security CORS",
        transitionQualifiers: ["client_redirect"],
        windowId: 3
      }
    ]);
  });

  it("suppresses duplicate navigation events for the same tab and sanitized URL", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    await service.capture(baseCandidate());
    const duplicateResult = await service.capture({
      ...baseCandidate(),
      source: "web_navigation_history_state"
    });

    expect(duplicateResult).toEqual({ status: "ignored", reason: "duplicate" });
    expect(sink.events()).toHaveLength(1);
  });

  it.each([
    ["unsupported scheme", "file:///Users/example/project/.env"],
    ["chrome page", "chrome://extensions/"],
    ["extension page", "chrome-extension://extension-id/onboarding.html"],
    ["browser internal page", "about:blank"]
  ])("rejects %s", async (_label, url) => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    const result = await service.capture({ ...baseCandidate(), url });

    expect(result).toMatchObject({
      status: "ignored",
      reason: "sanitation_rejected",
      rejectionReason: "unsupported_scheme"
    });
    expect(sink.events()).toEqual([]);
  });

  it("ignores subframe navigation", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    await expect(service.capture({ ...baseCandidate(), frameId: 1 })).resolves.toEqual({
      status: "ignored",
      reason: "subframe"
    });
    expect(sink.events()).toEqual([]);
  });

  it.each([
    ["missing consent", missingConsentState()],
    ["stale consent", staleConsentState()]
  ])("ignores navigation with %s", async (_label, consentState) => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({
      consentReader: new StaticCaptureConsentReader(consentState),
      sink
    });

    await expect(service.capture(baseCandidate())).resolves.toEqual({
      status: "ignored",
      reason: "missing_consent"
    });
    expect(sink.events()).toEqual([]);
  });

  it("stops before sanitation and persistence when capture is paused", async () => {
    const sink = new RecordingCaptureEventSink();
    const sanitizer = createUrlQuerySanitizer(defaultCaptureSanitationPolicy);
    const sanitizeSpy = vi.spyOn(sanitizer, "sanitize");
    const service = createService({
      controlReader: new StaticCaptureControlReader({ status: "blocked", reason: "paused" }),
      sanitizer,
      sink
    });

    await expect(service.capture(baseCandidate())).resolves.toEqual({
      status: "ignored",
      reason: "paused"
    });
    expect(sanitizeSpy).not.toHaveBeenCalled();
    expect(sink.events()).toEqual([]);
  });

  it("stops before sanitation and persistence for excluded domains", async () => {
    const sink = new RecordingCaptureEventSink();
    const sanitizer = createUrlQuerySanitizer(defaultCaptureSanitationPolicy);
    const sanitizeSpy = vi.spyOn(sanitizer, "sanitize");
    const service = createService({
      controlReader: new StaticCaptureControlReader({ status: "blocked", reason: "domain_excluded", domain: "example.test" }),
      sanitizer,
      sink
    });

    await expect(service.capture(baseCandidate())).resolves.toEqual({
      status: "ignored",
      reason: "domain_excluded"
    });
    expect(sanitizeSpy).not.toHaveBeenCalled();
    expect(sink.events()).toEqual([]);
  });

  it("checks consent before local controls", async () => {
    const controlReader = new RecordingCaptureControlReader({ status: "blocked", reason: "paused" });
    const service = createService({
      consentReader: new StaticCaptureConsentReader(missingConsentState()),
      controlReader
    });

    await expect(service.capture(baseCandidate())).resolves.toEqual({
      status: "ignored",
      reason: "missing_consent"
    });
    expect(controlReader.evaluatedUrls()).toEqual([]);
  });

  it("ignores sanitizer rejection for private hosts", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    const result = await service.capture({
      ...baseCandidate(),
      url: "http://localhost:3000/debug"
    });

    expect(result).toMatchObject({
      status: "ignored",
      reason: "sanitation_rejected",
      rejectionReason: "private_or_local_host"
    });
    expect(sink.events()).toEqual([]);
  });

  it("creates immutable capture events", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    const result = await service.capture(baseCandidate());

    if (result.status !== "captured") {
      throw new Error("Expected capture result.");
    }

    expect(Object.isFrozen(result.event)).toBe(true);
    expect(Object.isFrozen(result.event.removedQueryParameters)).toBe(true);
    expect(Object.isFrozen(result.event.transitionQualifiers)).toBe(true);
  });

  it("creates timestamps and search metadata", async () => {
    const sink = new RecordingCaptureEventSink();
    const service = createService({ sink });

    const result = await service.capture({
      ...baseCandidate(),
      url: "https://www.google.com/search?q=Spring%20Security%20CORS&utm_source=test",
      title: "Spring Security CORS - Google Search"
    });

    expect(result.status).toBe("captured");
    expect(sink.events()[0]).toMatchObject({
      capturedAt: CAPTURED_AT.toISOString(),
      resourceType: "search",
      searchEngine: "google",
      searchQuery: "Spring Security CORS"
    });
  });

  it("remains safe after service worker lifecycle reset", async () => {
    const sink = new RecordingCaptureEventSink();
    const firstWorkerService = createService({ sink });
    await firstWorkerService.capture(baseCandidate());

    const restartedWorkerService = createService({
      now: () => new Date("2026-07-12T11:00:01.000Z"),
      sink
    });
    const result = await restartedWorkerService.capture(baseCandidate());

    expect(result.status).toBe("captured");
    expect(sink.events()).toHaveLength(2);
  });
});

function createService(options: Partial<CreateServiceOptions> = {}): NavigationCaptureService {
  return new NavigationCaptureService({
    consentReader: options.consentReader ?? new StaticCaptureConsentReader(acceptedConsentState()),
    controlReader: options.controlReader ?? new StaticCaptureControlReader({ status: "allowed" }),
    now: options.now ?? (() => CAPTURED_AT),
    sanitizer: options.sanitizer ?? createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
    sink: options.sink ?? new RecordingCaptureEventSink()
  });
}

function baseCandidate() {
  return {
    frameId: 0,
    source: "web_navigation_committed" as const,
    tabId: 42,
    title: "Example docs",
    url: "https://example.test/docs/cors",
    windowId: 3
  };
}

function acceptedConsentState(): CaptureConsentState {
  return {
    accepted: true,
    consentVersion: "capture-consent-v1",
    decidedAt: "2026-07-12T10:30:00.000Z",
    status: "accepted"
  };
}

function missingConsentState(): CaptureConsentState {
  return {
    accepted: false,
    consentVersion: null,
    decidedAt: null,
    status: "missing"
  };
}

function staleConsentState(): CaptureConsentState {
  return {
    accepted: false,
    consentVersion: "capture-consent-v0",
    decidedAt: "2026-07-12T10:30:00.000Z",
    requiredConsentVersion: "capture-consent-v1",
    status: "stale"
  };
}

interface CreateServiceOptions {
  readonly consentReader: CaptureConsentReader;
  readonly controlReader: CaptureControlReader;
  readonly now: () => Date;
  readonly sanitizer: ReturnType<typeof createUrlQuerySanitizer>;
  readonly sink: RecordingCaptureEventSink;
}

class StaticCaptureConsentReader implements CaptureConsentReader {
  private readonly state: CaptureConsentState;

  constructor(state: CaptureConsentState) {
    this.state = state;
  }

  readState(): Promise<CaptureConsentState> {
    return Promise.resolve(this.state);
  }
}

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

class StaticCaptureControlReader implements CaptureControlReader {
  private readonly decision: CaptureControlDecision;

  constructor(decision: CaptureControlDecision) {
    this.decision = decision;
  }

  evaluateUrl(): Promise<CaptureControlDecision> {
    return Promise.resolve(this.decision);
  }

  readState(): Promise<never> {
    return Promise.reject(new Error("readState is not used by navigation capture."));
  }
}

class RecordingCaptureControlReader implements CaptureControlReader {
  private readonly decision: CaptureControlDecision;
  private readonly urls: string[] = [];

  constructor(decision: CaptureControlDecision) {
    this.decision = decision;
  }

  evaluateUrl(url: string): Promise<CaptureControlDecision> {
    this.urls.push(url);
    return Promise.resolve(this.decision);
  }

  readState(): Promise<never> {
    return Promise.reject(new Error("readState is not used by navigation capture."));
  }

  evaluatedUrls(): readonly string[] {
    return this.urls;
  }
}
