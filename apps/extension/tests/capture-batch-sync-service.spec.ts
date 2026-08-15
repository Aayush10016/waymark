import { describe, expect, it } from "vitest";
import type { CaptureBatchApiClient, CaptureBatchUploadCommand, CaptureBatchUploadResult } from "../src/services/capture-sync";
import { CaptureBatchSyncService, FetchCaptureBatchApiClient } from "../src/services/capture-sync";
import type {
  CaptureEventQueueRepository,
  CaptureQueueBatchSelectionOptions,
  CaptureQueueReadOptions,
  CaptureQueueStatistics,
  CaptureQueueWriteResult,
  StoredCaptureQueueRecord
} from "../src/storage/capture-queue";
import type { CaptureEvent } from "../src/services/navigation-capture";

describe("CaptureBatchSyncService", () => {
  it("uploads a bounded batch with generated OpenAPI request shape and removes accepted events", async () => {
    const queue = new InMemoryCaptureQueue([
      queueRecord("record-1", { canonicalUrl: "https://example.test/first", capturedAt: "2026-07-13T08:00:00.000Z" }),
      queueRecord("record-2", { canonicalUrl: "https://example.test/second", capturedAt: "2026-07-13T08:00:01.000Z" })
    ]);
    const apiClient = new SequencedCaptureBatchApiClient({
      status: "processed",
      response: {
        accepted: [{ clientEventId: "record-1" }, { clientEventId: "record-2" }],
        duplicate: [],
        rejected: []
      }
    });
    const service = createService({ apiClient, queue });

    const result = await service.synchronize();

    expect(result.status).toBe("processed");
    expect(result.statistics).toMatchObject({
      acceptedCount: 2,
      duplicateCount: 0,
      rejectedCount: 0,
      removedCount: 2,
      retainedCount: 0,
      selectedCount: 2,
      totalRecordsAfter: 0,
      totalRecordsBefore: 2
    });
    expect(await queue.read()).toEqual([]);
    expect(apiClient.commands()).toHaveLength(1);
    expect(apiClient.commands()[0]).toMatchObject({
      accessToken: "access-token",
      idempotencyKey: "capture-batch:record-1,record-2",
      request: {
        clientInstanceId: "extension-instance-1",
        events: [
          {
            browserEventTimeMilliseconds: 100,
            clientEventId: "record-1",
            clientSequence: null,
            navigationType: "link",
            parentClientEventId: null,
            problemSessionId: null,
            resourceType: "DOCUMENTATION",
            source: "WEB_NAVIGATION_COMMITTED",
            tabSessionId: null,
            title: "CORS :: Spring Security",
            transitionQualifiers: ["from_address_bar"],
            url: "https://example.test/first",
            occurredAt: "2026-07-13T08:00:00.000Z"
          },
          {
            clientEventId: "record-2",
            url: "https://example.test/second",
            occurredAt: "2026-07-13T08:00:01.000Z"
          }
        ]
      }
    });
  });

  it("removes accepted and duplicate acknowledgments while retaining rejected events", async () => {
    const queue = new InMemoryCaptureQueue([
      queueRecord("accepted-record"),
      queueRecord("duplicate-record"),
      queueRecord("rejected-record")
    ]);
    const service = createService({
      apiClient: new SequencedCaptureBatchApiClient({
        status: "processed",
        response: {
          accepted: [{ clientEventId: "accepted-record" }],
          duplicate: [{ clientEventId: "duplicate-record" }],
          rejected: [{ clientEventId: "rejected-record", code: "INVALID_URL" }]
        }
      }),
      queue
    });

    const result = await service.synchronize();

    expect(result.status).toBe("processed");
    expect(result.removedRecordIds).toEqual(["accepted-record", "duplicate-record"]);
    expect(result.retainedRecordIds).toEqual(["rejected-record"]);
    expect(await queue.read()).toEqual([expect.objectContaining({ id: "rejected-record" })]);
  });

  it("retains missing-consent responses because they are not acknowledgments", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("consent-record")]);
    const service = createService({
      apiClient: new SequencedCaptureBatchApiClient({
        status: "processed",
        response: {
          accepted: [],
          duplicate: [],
          rejected: [{ clientEventId: "consent-record", code: "CONSENT_REQUIRED" }]
        }
      }),
      queue
    });

    const result = await service.synchronize();

    expect(result.status).toBe("processed");
    expect(result.statistics.rejectedCount).toBe(1);
    expect(result.statistics.removedCount).toBe(0);
    expect(result.retainedRecordIds).toEqual(["consent-record"]);
    expect(await queue.size()).toBe(1);
  });

  it("does not upload or remove records when authentication is unavailable", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("auth-record")]);
    const apiClient = new SequencedCaptureBatchApiClient();
    const service = createService({ accessToken: null, apiClient, queue });

    const result = await service.synchronize();

    expect(result).toMatchObject({
      failureReason: "missing_access_token",
      status: "authentication_required"
    });
    expect(apiClient.commands()).toEqual([]);
    expect(await queue.size()).toBe(1);
  });

  it("retains records when the server rejects the access token", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("server-auth-record")]);
    const service = createService({
      apiClient: new SequencedCaptureBatchApiClient({ status: "authentication_rejected" }),
      queue
    });

    const result = await service.synchronize();

    expect(result).toMatchObject({
      failureReason: "server_rejected_access_token",
      status: "authentication_required"
    });
    expect(await queue.size()).toBe(1);
  });

  it("classifies payload-too-large as permanent without removing records", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("payload-record")]);
    const service = createService({
      apiClient: new SequencedCaptureBatchApiClient({ status: "payload_too_large" }),
      queue
    });

    const result = await service.synchronize();

    expect(result).toMatchObject({
      failureReason: "payload_too_large",
      status: "permanent_failure"
    });
    expect(result.retainedRecordIds).toEqual(["payload-record"]);
    expect(await queue.size()).toBe(1);
  });

  it("classifies rate limits and offline network errors as retryable without removing records", async () => {
    const rateLimitedQueue = new InMemoryCaptureQueue([queueRecord("rate-limited-record")]);
    const offlineQueue = new InMemoryCaptureQueue([queueRecord("offline-record")]);

    const rateLimitedResult = await createService({
      apiClient: new SequencedCaptureBatchApiClient({ status: "rate_limited" }),
      queue: rateLimitedQueue
    }).synchronize();
    const offlineResult = await createService({
      apiClient: new SequencedCaptureBatchApiClient({ status: "network_error" }),
      queue: offlineQueue
    }).synchronize();

    expect(rateLimitedResult).toMatchObject({ failureReason: "rate_limited", status: "retryable_failure" });
    expect(offlineResult).toMatchObject({ failureReason: "network_error", status: "retryable_failure" });
    expect(await rateLimitedQueue.size()).toBe(1);
    expect(await offlineQueue.size()).toBe(1);
  });

  it("retains records when the server response is malformed", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("malformed-record")]);
    const service = createService({
      apiClient: new SequencedCaptureBatchApiClient({ status: "malformed_response" }),
      queue
    });

    const result = await service.synchronize();

    expect(result).toMatchObject({
      failureReason: "malformed_response",
      status: "malformed_response"
    });
    expect(await queue.size()).toBe(1);
  });

  it("returns empty without requesting auth or uploading when the queue has no batch", async () => {
    const queue = new InMemoryCaptureQueue();
    const authTokenProvider = new StaticAuthTokenProvider("access-token");
    const apiClient = new SequencedCaptureBatchApiClient();
    const service = createService({ apiClient, authTokenProvider, queue });

    const result = await service.synchronize();

    expect(result.status).toBe("empty");
    expect(result.statistics.selectedCount).toBe(0);
    expect(authTokenProvider.calls()).toBe(0);
    expect(apiClient.commands()).toEqual([]);
  });

  it("preserves queue batch ordering during upload", async () => {
    const queue = new InMemoryCaptureQueue([
      queueRecord("first-record", { enqueuedAt: "2026-07-13T08:00:00.000Z" }),
      queueRecord("second-record", { enqueuedAt: "2026-07-13T08:00:01.000Z" }),
      queueRecord("third-record", { enqueuedAt: "2026-07-13T08:00:02.000Z" })
    ]);
    const apiClient = new SequencedCaptureBatchApiClient({
      status: "processed",
      response: {
        accepted: [],
        duplicate: [],
        rejected: []
      }
    });

    await createService({ apiClient, maxBatchSize: 2, queue }).synchronize();

    expect(apiClient.commands()[0]?.request.events.map((event) => event.clientEventId)).toEqual([
      "first-record",
      "second-record"
    ]);
  });

  it("deduplicates concurrent synchronization attempts inside one extension runtime boundary", async () => {
    const queue = new InMemoryCaptureQueue([queueRecord("concurrent-record")]);
    const apiClient = new DeferredCaptureBatchApiClient({
      status: "processed",
      response: {
        accepted: [{ clientEventId: "concurrent-record" }],
        duplicate: [],
        rejected: []
      }
    });
    const service = createService({ apiClient, queue });

    const first = service.synchronize();
    const second = service.synchronize();
    apiClient.resolve();

    await expect(Promise.all([first, second])).resolves.toEqual([
      expect.objectContaining({ status: "processed" }),
      expect.objectContaining({ status: "processed" })
    ]);
    expect(apiClient.commands()).toHaveLength(1);
    expect(await queue.size()).toBe(0);
  });
});

describe("FetchCaptureBatchApiClient", () => {
  it("maps malformed JSON responses without trusting the server body", async () => {
    const client = new FetchCaptureBatchApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(Response.json({ accepted: "invalid" }))
    });

    await expect(client.upload(uploadCommand())).resolves.toEqual({ status: "malformed_response" });
  });

  it("maps authentication, payload, rate-limit, and offline failures", async () => {
    const authenticationClient = new FetchCaptureBatchApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 401 }))
    });
    const payloadClient = new FetchCaptureBatchApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 413 }))
    });
    const rateLimitClient = new FetchCaptureBatchApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 429 }))
    });
    const offlineClient = new FetchCaptureBatchApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.reject(new TypeError("offline"))
    });

    await expect(authenticationClient.upload(uploadCommand())).resolves.toEqual({ status: "authentication_rejected" });
    await expect(payloadClient.upload(uploadCommand())).resolves.toEqual({ status: "payload_too_large" });
    await expect(rateLimitClient.upload(uploadCommand())).resolves.toEqual({ status: "rate_limited" });
    await expect(offlineClient.upload(uploadCommand())).resolves.toEqual({ status: "network_error" });
  });
});

function createService(options: CreateServiceOptions = {}): CaptureBatchSyncService {
  return new CaptureBatchSyncService({
    apiClient: options.apiClient ?? new SequencedCaptureBatchApiClient(),
    authTokenProvider:
      options.authTokenProvider ?? new StaticAuthTokenProvider(options.accessToken === undefined ? "access-token" : options.accessToken),
    clientInstanceId: "extension-instance-1",
    queue: options.queue ?? new InMemoryCaptureQueue(),
    ...(options.maxBatchSize === undefined ? {} : { maxBatchSize: options.maxBatchSize })
  });
}

function queueRecord(id: string, overrides: Partial<StoredCaptureQueueRecord> = {}): StoredCaptureQueueRecord {
  return Object.freeze({
    browserEventTime: 100,
    canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
    capturedAt: "2026-07-13T08:00:00.000Z",
    enqueuedAt: "2026-07-13T08:00:00.000Z",
    hostname: "docs.spring.io",
    id,
    navigationType: "link",
    openerTabId: null,
    policyVersion: "url-query-sanitation-v1",
    problemSessionId: null,
    removedQueryParameters: Object.freeze([]),
    resourceType: "documentation",
    schemaVersion: 1,
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

function uploadCommand(): CaptureBatchUploadCommand {
  return {
    accessToken: "access-token",
    idempotencyKey: "capture-batch:record-1",
    request: {
      clientInstanceId: "extension-instance-1",
      events: []
    }
  };
}

interface CreateServiceOptions {
  readonly accessToken?: string | null;
  readonly apiClient?: CaptureBatchApiClient;
  readonly authTokenProvider?: StaticAuthTokenProvider;
  readonly maxBatchSize?: number;
  readonly queue?: CaptureEventQueueRepository;
}

class InMemoryCaptureQueue implements CaptureEventQueueRepository {
  private readonly records: StoredCaptureQueueRecord[];

  constructor(records: readonly StoredCaptureQueueRecord[] = []) {
    this.records = [...records];
  }

  append(event: CaptureEvent): Promise<StoredCaptureQueueRecord> {
    const record = queueRecord(`record-${this.records.length + 1}`, event);
    this.records.push(record);
    return Promise.resolve(record);
  }

  write(event: CaptureEvent): Promise<CaptureQueueWriteResult> {
    return this.append(event).then((record) => ({ evictedRecords: [], record }));
  }

  read(options: CaptureQueueReadOptions = {}): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.resolve(this.orderedRecords().slice(0, options.limit));
  }

  selectBatch(options: CaptureQueueBatchSelectionOptions = {}): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.resolve(this.orderedRecords().slice(0, options.maxBatchSize));
  }

  remove(ids: readonly string[]): Promise<void> {
    const idsToRemove = new Set(ids);
    for (let index = this.records.length - 1; index >= 0; index -= 1) {
      const record = this.records[index];
      if (record !== undefined && idsToRemove.has(record.id)) {
        this.records.splice(index, 1);
      }
    }
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.records.splice(0, this.records.length);
    return Promise.resolve();
  }

  size(): Promise<number> {
    return Promise.resolve(this.records.length);
  }

  statistics(): Promise<CaptureQueueStatistics> {
    const records = this.orderedRecords();
    return Promise.resolve({
      maxBatchSize: 50,
      maxRecords: 1_000,
      newestEnqueuedAt: records.at(-1)?.enqueuedAt ?? null,
      oldestEnqueuedAt: records[0]?.enqueuedAt ?? null,
      remainingCapacity: 1_000 - records.length,
      totalRecords: records.length
    });
  }

  private orderedRecords(): StoredCaptureQueueRecord[] {
    return [...this.records].sort((left, right) => {
      const enqueuedComparison = left.enqueuedAt.localeCompare(right.enqueuedAt);
      return enqueuedComparison === 0 ? left.id.localeCompare(right.id) : enqueuedComparison;
    });
  }
}

class StaticAuthTokenProvider {
  private callCount = 0;
  private readonly accessToken: string | null;

  constructor(accessToken: string | null) {
    this.accessToken = accessToken;
  }

  getValidAccessToken(): Promise<string | null> {
    this.callCount += 1;
    return Promise.resolve(this.accessToken);
  }

  calls(): number {
    return this.callCount;
  }
}

class SequencedCaptureBatchApiClient implements CaptureBatchApiClient {
  private readonly commandLog: CaptureBatchUploadCommand[] = [];
  private readonly results: CaptureBatchUploadResult[];

  constructor(...results: CaptureBatchUploadResult[]) {
    this.results = results;
  }

  upload(command: CaptureBatchUploadCommand): Promise<CaptureBatchUploadResult> {
    this.commandLog.push(command);
    return Promise.resolve(this.results.shift() ?? emptyProcessedResult());
  }

  commands(): readonly CaptureBatchUploadCommand[] {
    return this.commandLog;
  }
}

class DeferredCaptureBatchApiClient implements CaptureBatchApiClient {
  private readonly commandLog: CaptureBatchUploadCommand[] = [];
  private readonly result: CaptureBatchUploadResult;
  private alreadyResolved = false;
  private resolver: (() => void) | null = null;

  constructor(result: CaptureBatchUploadResult) {
    this.result = result;
  }

  upload(command: CaptureBatchUploadCommand): Promise<CaptureBatchUploadResult> {
    this.commandLog.push(command);
    if (this.alreadyResolved) {
      return Promise.resolve(this.result);
    }
    return new Promise((resolve) => {
      this.resolver = () => resolve(this.result);
    });
  }

  resolve(): void {
    this.alreadyResolved = true;
    this.resolver?.();
  }

  commands(): readonly CaptureBatchUploadCommand[] {
    return this.commandLog;
  }
}

function emptyProcessedResult(): CaptureBatchUploadResult {
  return {
    status: "processed",
    response: {
      accepted: [],
      duplicate: [],
      rejected: []
    }
  };
}
