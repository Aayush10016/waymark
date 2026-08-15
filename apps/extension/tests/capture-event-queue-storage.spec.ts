import { IDBFactory } from "fake-indexeddb";
import { describe, expect, it } from "vitest";
import { createUrlQuerySanitizer, defaultCaptureSanitationPolicy } from "../src/services/capture-policy";
import { NavigationCaptureService } from "../src/services/navigation-capture";
import type { CaptureConsentState } from "../src/services/consent";
import type { CaptureControlDecision, CaptureControlReader } from "../src/services/capture-controls";
import type { CaptureEvent } from "../src/services/navigation-capture";
import {
  CAPTURE_QUEUE_DATABASE_VERSION,
  CAPTURE_QUEUE_RECORD_SCHEMA_VERSION,
  CAPTURE_QUEUE_STORE_NAME,
  DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE,
  IndexedDbCaptureEventQueue
} from "../src/storage/capture-queue";

const ENQUEUED_AT = new Date("2026-07-13T07:00:00.000Z");
const CAPTURED_AT = "2026-07-13T06:59:59.000Z";

describe("IndexedDbCaptureEventQueue", () => {
  it("creates the IndexedDB database and schema during version upgrade", async () => {
    const idbFactory = new IDBFactory();
    const queue = createQueue({ idbFactory });

    await queue.append(captureEvent());

    const database = await openRawDatabase(idbFactory, "trail-capture-queue-test", CAPTURE_QUEUE_DATABASE_VERSION);
    expect(database.objectStoreNames.contains(CAPTURE_QUEUE_STORE_NAME)).toBe(true);
    expect(database.version).toBe(CAPTURE_QUEUE_DATABASE_VERSION);
    database.close();
  });

  it("appends a sanitized capture event with a stable identifier", async () => {
    const queue = createQueue({ idFactory: () => "capture-event-1" });

    const record = await queue.append(captureEvent());

    expect(record).toMatchObject({
      id: "capture-event-1",
      schemaVersion: CAPTURE_QUEUE_RECORD_SCHEMA_VERSION,
      canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
      policyVersion: "url-query-sanitation-v1",
      enqueuedAt: ENQUEUED_AT.toISOString()
    });
    await expect(queue.read()).resolves.toEqual([record]);
  });

  it("writes before future network work by durably appending through the sink boundary", async () => {
    const queue = createQueue({ idFactory: () => "write-event-1" });

    await queue.emit(captureEvent({ canonicalUrl: "https://example.test/write-first" }));

    await expect(queue.read()).resolves.toEqual([
      expect.objectContaining({
        id: "write-event-1",
        canonicalUrl: "https://example.test/write-first"
      })
    ]);
  });

  it("appends multiple events and reads them ordered by enqueue timestamp", async () => {
    const idbFactory = new IDBFactory();
    const queue = createQueue({
      idbFactory,
      idFactory: sequentialIds("event"),
      now: sequentialDates([
        "2026-07-13T07:00:02.000Z",
        "2026-07-13T07:00:00.000Z",
        "2026-07-13T07:00:01.000Z"
      ])
    });

    await queue.append(captureEvent({ canonicalUrl: "https://example.test/third" }));
    await queue.append(captureEvent({ canonicalUrl: "https://example.test/first" }));
    await queue.append(captureEvent({ canonicalUrl: "https://example.test/second" }));

    await expect(queue.read()).resolves.toEqual([
      expect.objectContaining({ canonicalUrl: "https://example.test/first" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/second" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/third" })
    ]);
  });

  it("survives browser and service-worker restarts with the same IndexedDB database", async () => {
    const idbFactory = new IDBFactory();
    const firstWorkerQueue = createQueue({ idbFactory, idFactory: () => "persisted-event" });

    await firstWorkerQueue.append(captureEvent());

    const restartedWorkerQueue = createQueue({ idbFactory });
    await expect(restartedWorkerQueue.read()).resolves.toEqual([
      expect.objectContaining({
        id: "persisted-event",
        canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html"
      })
    ]);
  });

  it("returns queue size and removes records only when explicitly requested", async () => {
    const queue = createQueue({ idFactory: sequentialIds("remove") });
    const first = await queue.append(captureEvent({ canonicalUrl: "https://example.test/one" }));
    const second = await queue.append(captureEvent({ canonicalUrl: "https://example.test/two" }));

    await expect(queue.size()).resolves.toBe(2);

    await queue.remove([first.id]);

    await expect(queue.size()).resolves.toBe(1);
    await expect(queue.read()).resolves.toEqual([expect.objectContaining({ id: second.id })]);
  });

  it("supports future batch retrieval without removing records", async () => {
    const queue = createQueue({ idFactory: sequentialIds("batch") });
    await queue.append(captureEvent({ canonicalUrl: "https://example.test/one" }));
    await queue.append(captureEvent({ canonicalUrl: "https://example.test/two" }));
    await queue.append(captureEvent({ canonicalUrl: "https://example.test/three" }));

    const firstTwoRecords = await queue.read({ limit: 2 });

    expect(firstTwoRecords).toHaveLength(2);
    await expect(queue.size()).resolves.toBe(3);
  });

  it("selects deterministic batches in enqueue order with configurable batch size limits", async () => {
    const queue = createQueue({
      idFactory: sequentialIds("selected"),
      maxBatchSize: 2,
      now: sequentialDates([
        "2026-07-13T07:00:00.000Z",
        "2026-07-13T07:00:02.000Z",
        "2026-07-13T07:00:01.000Z"
      ])
    });

    await queue.write(captureEvent({ canonicalUrl: "https://example.test/first" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/third" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/second" }));

    await expect(queue.selectBatch({ maxBatchSize: 50 })).resolves.toEqual([
      expect.objectContaining({ canonicalUrl: "https://example.test/first" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/second" })
    ]);
    await expect(queue.size()).resolves.toBe(3);
  });

  it("uses the default maximum batch size when no batch size is requested", async () => {
    const queue = createQueue({
      idFactory: sequentialIds("default-batch"),
      maxBatchSize: DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE
    });

    for (let index = 0; index < DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE + 1; index += 1) {
      await queue.write(captureEvent({ canonicalUrl: `https://example.test/default-batch/${index}` }));
    }

    await expect(queue.selectBatch()).resolves.toHaveLength(DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE);
  });

  it("enforces capture queue capacity by evicting oldest capture records and keeping newest", async () => {
    const queue = createQueue({
      idFactory: sequentialIds("capacity"),
      maxRecords: 3,
      now: sequentialDates([
        "2026-07-13T07:00:00.000Z",
        "2026-07-13T07:00:01.000Z",
        "2026-07-13T07:00:02.000Z",
        "2026-07-13T07:00:03.000Z"
      ])
    });

    await queue.write(captureEvent({ canonicalUrl: "https://example.test/oldest" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/middle" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/newer" }));
    const result = await queue.write(captureEvent({ canonicalUrl: "https://example.test/newest" }));

    expect(result.evictedRecords).toEqual([expect.objectContaining({ canonicalUrl: "https://example.test/oldest" })]);
    await expect(queue.read()).resolves.toEqual([
      expect.objectContaining({ canonicalUrl: "https://example.test/middle" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/newer" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/newest" })
    ]);
  });

  it("keeps capacity enforcement scoped to capture records", async () => {
    const queue = createQueue({ maxRecords: 1, idFactory: sequentialIds("capture-only") });

    await queue.write(captureEvent({ canonicalUrl: "https://example.test/evicted-capture" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/retained-capture" }));

    await expect(queue.read()).resolves.toEqual([
      expect.objectContaining({ canonicalUrl: "https://example.test/retained-capture" })
    ]);
  });

  it("reports queue statistics required by future sync surfaces", async () => {
    const queue = createQueue({
      idFactory: sequentialIds("stats"),
      maxBatchSize: 2,
      maxRecords: 5,
      now: sequentialDates(["2026-07-13T07:00:00.000Z", "2026-07-13T07:00:01.000Z"])
    });

    await queue.write(captureEvent({ canonicalUrl: "https://example.test/stats-one" }));
    await queue.write(captureEvent({ canonicalUrl: "https://example.test/stats-two" }));

    await expect(queue.statistics()).resolves.toEqual({
      maxBatchSize: 2,
      maxRecords: 5,
      newestEnqueuedAt: "2026-07-13T07:00:01.000Z",
      oldestEnqueuedAt: "2026-07-13T07:00:00.000Z",
      remainingCapacity: 3,
      totalRecords: 2
    });
  });

  it("persists policy version, enqueue timestamp, and sanitized metadata only", async () => {
    const queue = createQueue({ idFactory: () => "sanitized-only" });

    await queue.append(
      captureEvent({
        canonicalUrl: "https://www.google.com/search?q=Spring%20Security%20CORS",
        hostname: "www.google.com",
        removedQueryParameters: ["utm_source"],
        resourceType: "search",
        searchEngine: "google",
        searchQuery: "Spring Security CORS",
        title: "Spring Security CORS - Google Search"
      })
    );

    const storedRecord = (await queue.read())[0];

    expect(storedRecord).toMatchObject({
      id: "sanitized-only",
      policyVersion: "url-query-sanitation-v1",
      enqueuedAt: ENQUEUED_AT.toISOString(),
      canonicalUrl: "https://www.google.com/search?q=Spring%20Security%20CORS",
      searchQuery: "Spring Security CORS"
    });
    expect(storedRecord?.removedQueryParameters).toEqual(["utm_source"]);
    expect(JSON.stringify(storedRecord)).not.toContain("utm_source=test");
  });

  it("handles large queues in enqueue order", async () => {
    const queue = createQueue({
      idFactory: sequentialIds("large"),
      now: sequentialDates(
        Array.from({ length: 150 }, (_, index) => new Date(Date.parse("2026-07-13T07:00:00.000Z") + index * 1_000).toISOString())
      )
    });

    for (let index = 0; index < 150; index += 1) {
      await queue.append(captureEvent({ canonicalUrl: `https://example.test/docs/${index}` }));
    }

    const records = await queue.read();
    expect(records).toHaveLength(150);
    expect(records[0]?.canonicalUrl).toBe("https://example.test/docs/0");
    expect(records[149]?.canonicalUrl).toBe("https://example.test/docs/149");
  });

  it("returns immutable stored records", async () => {
    const queue = createQueue();

    await queue.append(captureEvent());
    const record = (await queue.read())[0];

    expect(Object.isFrozen(record)).toBe(true);
    expect(Object.isFrozen(record?.removedQueryParameters)).toBe(true);
    expect(Object.isFrozen(record?.transitionQualifiers)).toBe(true);
  });

  it("is used as a CaptureEventSink by the navigation capture pipeline", async () => {
    const queue = createQueue();
    const service = new NavigationCaptureService({
      consentReader: new StaticCaptureConsentReader(acceptedConsentState()),
      controlReader: new StaticCaptureControlReader({ status: "allowed" }),
      now: () => new Date(CAPTURED_AT),
      sanitizer: createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
      sink: queue
    });

    await service.capture({
      frameId: 0,
      source: "web_navigation_committed",
      tabId: 9,
      title: "Spring Security CORS - Google Search",
      url: "https://www.google.com/search?q=Spring%20Security%20CORS&utm_source=test"
    });

    await expect(queue.read()).resolves.toEqual([
      expect.objectContaining({
        canonicalUrl: "https://www.google.com/search?q=Spring%20Security%20CORS",
        searchEngine: "google",
        searchQuery: "Spring Security CORS"
      })
    ]);
  });

  it("never stores events rejected by sanitation", async () => {
    const queue = createQueue();
    const service = new NavigationCaptureService({
      consentReader: new StaticCaptureConsentReader(acceptedConsentState()),
      controlReader: new StaticCaptureControlReader({ status: "allowed" }),
      now: () => new Date(CAPTURED_AT),
      sanitizer: createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
      sink: queue
    });

    await service.capture({
      frameId: 0,
      source: "web_navigation_committed",
      tabId: 9,
      title: "Local app",
      url: "http://localhost:3000/debug"
    });

    await expect(queue.size()).resolves.toBe(0);
  });
});

function createQueue(options: CreateQueueOptions = {}): IndexedDbCaptureEventQueue {
  const queueOptions = {
    databaseName: options.databaseName ?? "trail-capture-queue-test",
    idbFactory: options.idbFactory ?? new IDBFactory(),
    idFactory: options.idFactory ?? (() => "capture-event-id"),
    now: options.now ?? (() => ENQUEUED_AT)
  };

  return new IndexedDbCaptureEventQueue({
    ...queueOptions,
    ...(options.maxBatchSize === undefined ? {} : { maxBatchSize: options.maxBatchSize }),
    ...(options.maxRecords === undefined ? {} : { maxRecords: options.maxRecords })
  });
}

function captureEvent(overrides: Partial<CaptureEvent> = {}): CaptureEvent {
  return Object.freeze({
    browserEventTime: 100,
    canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
    capturedAt: CAPTURED_AT,
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
    transitionQualifiers: Object.freeze([]),
    windowId: 1,
    ...overrides
  });
}

function sequentialIds(prefix: string): () => string {
  let index = 0;
  return () => {
    index += 1;
    return `${prefix}-${index}`;
  };
}

function sequentialDates(values: readonly string[]): () => Date {
  let index = 0;
  return () => {
    const value = values[index];
    if (value === undefined) {
      throw new Error("Date fixture exhausted.");
    }

    index += 1;
    return new Date(value);
  };
}

function acceptedConsentState(): CaptureConsentState {
  return {
    accepted: true,
    consentVersion: "capture-consent-v1",
    decidedAt: "2026-07-13T06:00:00.000Z",
    status: "accepted"
  };
}

function openRawDatabase(idbFactory: IDBFactory, databaseName: string, version: number): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = idbFactory.open(databaseName, version);
    request.onerror = () => reject(request.error ?? new Error("Failed to open raw database."));
    request.onsuccess = () => resolve(request.result);
  });
}

interface CreateQueueOptions {
  readonly databaseName?: string;
  readonly idbFactory?: IDBFactory;
  readonly idFactory?: () => string;
  readonly maxBatchSize?: number;
  readonly maxRecords?: number;
  readonly now?: () => Date;
}

class StaticCaptureConsentReader {
  private readonly state: CaptureConsentState;

  constructor(state: CaptureConsentState) {
    this.state = state;
  }

  readState(): Promise<CaptureConsentState> {
    return Promise.resolve(this.state);
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
