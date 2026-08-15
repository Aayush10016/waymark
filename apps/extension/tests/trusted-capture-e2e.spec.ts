import { IDBFactory } from "fake-indexeddb";
import { describe, expect, it } from "vitest";
import { createUrlQuerySanitizer, defaultCaptureSanitationPolicy } from "../src/services/capture-policy";
import { CaptureControlRuntimeService } from "../src/services/capture-controls";
import { CaptureBatchSyncService } from "../src/services/capture-sync";
import type {
  CaptureBatchApiClient,
  CaptureBatchUploadCommand,
  CaptureBatchUploadResult
} from "../src/services/capture-sync";
import { CaptureConsentRuntimeService } from "../src/services/consent";
import { NavigationCaptureService } from "../src/services/navigation-capture";
import type { CaptureControlStorage, StoredCaptureControls } from "../src/storage/capture-controls";
import { IndexedDbCaptureEventQueue } from "../src/storage/capture-queue";
import type { CaptureConsentStorage, StoredCaptureConsent } from "../src/storage/consent/capture-consent-storage";

const CAPTURED_AT = new Date("2026-07-15T08:00:00.000Z");
const CONSENT_DECIDED_AT = new Date("2026-07-15T07:50:00.000Z");
const ENQUEUED_AT = new Date("2026-07-15T08:00:01.000Z");

describe("Trusted Capture privacy and restart suite", () => {
  it("fails closed before durable persistence when consent, pause, exclusion, or sanitation rejects capture", async () => {
    const runtime = createTrustedCaptureRuntime();

    await expect(runtime.capture.capture(candidate())).resolves.toEqual({
      status: "ignored",
      reason: "missing_consent"
    });
    await expect(runtime.queue.size()).resolves.toBe(0);

    await runtime.consent.accept();
    await runtime.controls.pause();
    await expect(runtime.capture.capture(candidate())).resolves.toEqual({
      status: "ignored",
      reason: "paused"
    });
    await expect(runtime.queue.size()).resolves.toBe(0);

    await runtime.controls.resume();
    await runtime.controls.excludeDomain("docs.spring.io");
    const restartedAfterExclusion = createTrustedCaptureRuntime({
      controlStorage: runtime.controlStorage,
      consentStorage: runtime.consentStorage,
      idbFactory: runtime.idbFactory
    });

    await expect(restartedAfterExclusion.capture.capture(candidate())).resolves.toEqual({
      status: "ignored",
      reason: "domain_excluded"
    });
    await expect(restartedAfterExclusion.queue.size()).resolves.toBe(0);

    await restartedAfterExclusion.controls.removeDomainExclusion("docs.spring.io");
    await expect(
      restartedAfterExclusion.capture.capture(candidate({ url: "http://localhost:3000/debug?token=secret" }))
    ).resolves.toMatchObject({
      status: "ignored",
      reason: "sanitation_rejected",
      rejectionReason: "private_or_local_host"
    });
    await expect(restartedAfterExclusion.queue.size()).resolves.toBe(0);
  });

  it("persists sanitized captures across restart and uploads only sanitized packet fields", async () => {
    const runtime = createTrustedCaptureRuntime();
    await runtime.consent.accept();

    const firstResult = await runtime.capture.capture(
      candidate({
        url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?token=secret&utm_source=ad&topic=cors#jwt",
        title: " Spring Security CORS docs "
      })
    );
    const duplicateResult = await runtime.capture.capture(
      candidate({
        source: "web_navigation_history_state",
        url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors"
      })
    );
    const secondResult = await runtime.capture.capture(
      candidate({
        url: "https://stackoverflow.com/search?q=Spring%20Security%20CORS&token=secret&utm_source=ad"
      })
    );

    expect(firstResult.status).toBe("captured");
    expect(duplicateResult).toEqual({ status: "ignored", reason: "duplicate" });
    expect(secondResult.status).toBe("captured");

    const restartedRuntime = createTrustedCaptureRuntime({
      controlStorage: runtime.controlStorage,
      consentStorage: runtime.consentStorage,
      idbFactory: runtime.idbFactory,
      idFactory: sequentialIds("ignored-after-restart")
    });
    await expect(restartedRuntime.queue.read()).resolves.toEqual([
      expect.objectContaining({
        id: "capture-record-1",
        canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors",
        hostname: "docs.spring.io",
        title: "Spring Security CORS docs"
      }),
      expect.objectContaining({
        id: "capture-record-2",
        canonicalUrl: "https://stackoverflow.com/search?q=Spring%20Security%20CORS",
        hostname: "stackoverflow.com",
        searchEngine: "stackoverflow",
        searchQuery: "Spring Security CORS"
      })
    ]);

    const apiClient = new RecordingCaptureBatchApiClient({
      status: "processed",
      response: {
        accepted: [{ clientEventId: "capture-record-1" }],
        duplicate: [{ clientEventId: "capture-record-2" }],
        rejected: []
      }
    });
    const sync = createSyncService({ apiClient, queue: restartedRuntime.queue });

    const syncResult = await sync.synchronize();

    expect(syncResult.status).toBe("processed");
    expect(syncResult.removedRecordIds).toEqual(["capture-record-1", "capture-record-2"]);
    await expect(restartedRuntime.queue.size()).resolves.toBe(0);

    const uploadedPacket = JSON.stringify(apiClient.commands()[0]?.request);
    expect(uploadedPacket).toContain("https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors");
    expect(uploadedPacket).toContain("https://stackoverflow.com/search?q=Spring%20Security%20CORS");
    expect(uploadedPacket).not.toContain("token=secret");
    expect(uploadedPacket).not.toContain("utm_source");
    expect(uploadedPacket).not.toContain("#jwt");
    expect(uploadedPacket).not.toContain("removedQueryParameters");
  });

  it("retains queued captures across offline and auth-expiry synchronization failures", async () => {
    const runtime = createTrustedCaptureRuntime();
    await runtime.consent.accept();
    await runtime.capture.capture(candidate());

    const offlineSync = createSyncService({
      apiClient: new RecordingCaptureBatchApiClient({ status: "network_error" }),
      queue: runtime.queue
    });

    await expect(offlineSync.synchronize()).resolves.toMatchObject({
      failureReason: "network_error",
      status: "retryable_failure"
    });
    await expect(runtime.queue.size()).resolves.toBe(1);

    const restartedRuntime = createTrustedCaptureRuntime({
      controlStorage: runtime.controlStorage,
      consentStorage: runtime.consentStorage,
      idbFactory: runtime.idbFactory
    });
    const authExpiredSync = createSyncService({
      apiClient: new RecordingCaptureBatchApiClient({ status: "authentication_rejected" }),
      queue: restartedRuntime.queue
    });

    await expect(authExpiredSync.synchronize()).resolves.toMatchObject({
      failureReason: "server_rejected_access_token",
      status: "authentication_required"
    });
    await expect(restartedRuntime.queue.read()).resolves.toEqual([
      expect.objectContaining({
        id: "capture-record-1",
        canonicalUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html"
      })
    ]);
  });
});

function createTrustedCaptureRuntime(options: Partial<CreateRuntimeOptions> = {}): TrustedCaptureRuntime {
  const idbFactory = options.idbFactory ?? new IDBFactory();
  const consentStorage = options.consentStorage ?? new InMemoryCaptureConsentStorage();
  const controlStorage = options.controlStorage ?? new InMemoryCaptureControlStorage();
  const queue = new IndexedDbCaptureEventQueue({
    databaseName: "trusted-capture-e2e",
    idbFactory,
    idFactory: options.idFactory ?? sequentialIds("capture-record"),
    now: options.enqueuedAt ?? (() => ENQUEUED_AT)
  });
  const consent = new CaptureConsentRuntimeService({
    storage: consentStorage,
    now: options.consentNow ?? (() => CONSENT_DECIDED_AT)
  });
  const controls = new CaptureControlRuntimeService({
    storage: controlStorage,
    now: options.controlNow ?? (() => CONSENT_DECIDED_AT)
  });

  return {
    capture: new NavigationCaptureService({
      consentReader: consent,
      controlReader: controls,
      now: options.capturedAt ?? (() => CAPTURED_AT),
      sanitizer: createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
      sink: queue
    }),
    consent,
    consentStorage,
    controls,
    controlStorage,
    idbFactory,
    queue
  };
}

function createSyncService(options: {
  readonly apiClient: CaptureBatchApiClient;
  readonly queue: IndexedDbCaptureEventQueue;
}): CaptureBatchSyncService {
  return new CaptureBatchSyncService({
    apiClient: options.apiClient,
    authTokenProvider: new StaticAuthTokenProvider("access-token"),
    clientInstanceId: "extension-instance-1",
    queue: options.queue
  });
}

function candidate(overrides: Partial<CaptureCandidateFixture> = {}): CaptureCandidateFixture {
  return {
    browserEventTime: 100,
    frameId: 0,
    navigationType: "link",
    openerTabId: null,
    source: "web_navigation_committed",
    tabId: 9,
    title: "CORS :: Spring Security",
    transitionQualifiers: ["from_address_bar"],
    url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
    windowId: 1,
    ...overrides
  };
}

function sequentialIds(prefix: string): () => string {
  let index = 0;
  return () => {
    index += 1;
    return `${prefix}-${index}`;
  };
}

interface TrustedCaptureRuntime {
  readonly capture: NavigationCaptureService;
  readonly consent: CaptureConsentRuntimeService;
  readonly consentStorage: InMemoryCaptureConsentStorage;
  readonly controls: CaptureControlRuntimeService;
  readonly controlStorage: InMemoryCaptureControlStorage;
  readonly idbFactory: IDBFactory;
  readonly queue: IndexedDbCaptureEventQueue;
}

interface CreateRuntimeOptions {
  readonly capturedAt: () => Date;
  readonly consentNow: () => Date;
  readonly consentStorage: InMemoryCaptureConsentStorage;
  readonly controlNow: () => Date;
  readonly controlStorage: InMemoryCaptureControlStorage;
  readonly enqueuedAt: () => Date;
  readonly idbFactory: IDBFactory;
  readonly idFactory: () => string;
}

type CaptureCandidateFixture = Parameters<NavigationCaptureService["capture"]>[0];

class InMemoryCaptureConsentStorage implements CaptureConsentStorage {
  private storedConsent: StoredCaptureConsent | null = null;

  read(): Promise<StoredCaptureConsent | null> {
    return Promise.resolve(this.storedConsent);
  }

  write(consent: StoredCaptureConsent): Promise<void> {
    this.storedConsent = consent;
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.storedConsent = null;
    return Promise.resolve();
  }
}

class InMemoryCaptureControlStorage implements CaptureControlStorage {
  private storedControls: StoredCaptureControls | null = null;

  read(): Promise<StoredCaptureControls | null> {
    return Promise.resolve(this.storedControls);
  }

  write(state: StoredCaptureControls): Promise<void> {
    this.storedControls = state;
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.storedControls = null;
    return Promise.resolve();
  }
}

class StaticAuthTokenProvider {
  constructor(private readonly accessToken: string | null) {}

  getValidAccessToken(): Promise<string | null> {
    return Promise.resolve(this.accessToken);
  }
}

class RecordingCaptureBatchApiClient implements CaptureBatchApiClient {
  private readonly commandLog: CaptureBatchUploadCommand[] = [];
  private readonly result: CaptureBatchUploadResult;

  constructor(result: CaptureBatchUploadResult) {
    this.result = result;
  }

  upload(command: CaptureBatchUploadCommand): Promise<CaptureBatchUploadResult> {
    this.commandLog.push(command);
    return Promise.resolve(this.result);
  }

  commands(): readonly CaptureBatchUploadCommand[] {
    return this.commandLog;
  }
}
