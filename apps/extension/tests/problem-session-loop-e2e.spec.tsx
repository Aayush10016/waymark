import { renderToStaticMarkup } from "react-dom/server";
import { IDBFactory } from "fake-indexeddb";
import { describe, expect, it } from "vitest";
import type { ActiveProblemSessionPayload } from "@trail/extension-messages";
import { createUrlQuerySanitizer, defaultCaptureSanitationPolicy } from "../src/services/capture-policy";
import { CaptureControlRuntimeService } from "../src/services/capture-controls";
import { CaptureConsentRuntimeService } from "../src/services/consent";
import { CaptureBatchSyncService, type CaptureBatchApiClient, type CaptureBatchUploadCommand, type CaptureBatchUploadResult } from "../src/services/capture-sync";
import { LocalFinalSolutionDraftService } from "../src/services/drafts";
import { NavigationCaptureService } from "../src/services/navigation-capture";
import {
  ProblemSessionRuntimeService,
  type FinishProblemSessionCommand,
  type ProblemSessionCommandApiClient,
  type ProblemSessionCommandApiResult,
  type ProblemSessionSolutionApiResult,
  type SaveProblemSessionSolutionCommand,
  type StartProblemSessionCommand
} from "../src/services/problem-session";
import { ResourceReplayService, type ReplayResourceOpener } from "../src/services/resource-replay";
import type { CaptureControlStorage, StoredCaptureControls } from "../src/storage/capture-controls";
import { IndexedDbCaptureEventQueue } from "../src/storage/capture-queue";
import type { CaptureConsentStorage, StoredCaptureConsent } from "../src/storage/consent/capture-consent-storage";
import { IndexedDbFinalSolutionDraftRepository } from "../src/storage/drafts";
import type {
  ProblemSessionRuntimeStorage,
  StoredProblemSessionRuntime
} from "../src/storage/problem-session";
import { ProblemSummary } from "../src/sidepanel/problem-session";

const SESSION_ID = "20000000-0000-4000-8000-000000000001";
const STARTED_AT = new Date("2026-07-16T08:00:00.000Z");
const CAPTURED_AT = new Date("2026-07-16T08:02:00.000Z");
const FINISHED_AT = new Date("2026-07-16T08:12:00.000Z");
const SOLVED_AT = new Date("2026-07-16T08:14:00.000Z");
const FINAL_SOLUTION = "Allowed localhost:3000 in CorsConfiguration and registered the JWT filter before UsernamePasswordAuthenticationFilter.";

describe("Problem Session complete loop E2E", () => {
  it("captures sanitized resources, creates a candidate, finishes, saves a draft, renders summary, and replays resources in order", async () => {
    const idbFactory = new IDBFactory();
    const captureRuntime = createCaptureRuntime({ idbFactory });
    await captureRuntime.consent.accept();

    await expect(
      captureRuntime.capture.capture(navigationCandidate({
        title: " Spring Security CORS docs ",
        url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?token=secret&utm_source=ad&topic=cors#jwt"
      }))
    ).resolves.toMatchObject({ status: "captured" });
    await expect(
      captureRuntime.capture.capture(navigationCandidate({
        title: "StackOverflow CORS answer",
        url: "https://stackoverflow.com/questions/123/spring-security-cors?utm_source=ad&token=secret"
      }))
    ).resolves.toMatchObject({ status: "captured" });

    const restartedCaptureRuntime = createCaptureRuntime({
      consentStorage: captureRuntime.consentStorage,
      controlStorage: captureRuntime.controlStorage,
      idFactory: sequentialIds("unused-after-restart"),
      idbFactory
    });
    const capturedRecords = await restartedCaptureRuntime.queue.read();
    expect(capturedRecords.map((record) => record.canonicalUrl)).toEqual([
      "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors",
      "https://stackoverflow.com/questions/123/spring-security-cors"
    ]);

    const problemApiClient = new SequencedProblemSessionApiClient([
      commandProcessed({ status: "CANDIDATE", version: 0, updatedAt: STARTED_AT.toISOString() }),
      commandProcessed({
        endedAt: FINISHED_AT.toISOString(),
        etag: "\"1\"",
        status: "REVIEWABLE",
        updatedAt: FINISHED_AT.toISOString(),
        version: 1
      }),
      solutionProcessed()
    ]);
    const problemStorage = new InMemoryProblemSessionRuntimeStorage();
    const problemSession = createProblemSessionService({
      apiClient: problemApiClient,
      nowSequence: [STARTED_AT, FINISHED_AT, SOLVED_AT],
      storage: problemStorage
    });

    await expect(problemSession.start("Spring Security CORS not working")).resolves.toMatchObject({
      commandStatus: "received",
      state: { activeSession: { id: SESSION_ID, status: "CANDIDATE" } }
    });
    await expect(problemSession.finish()).resolves.toMatchObject({
      commandStatus: "received",
      state: { activeSession: { endedAt: FINISHED_AT.toISOString(), status: "REVIEWABLE" } }
    });

    const draftService = new LocalFinalSolutionDraftService(new IndexedDbFinalSolutionDraftRepository({
      databaseName: "problem-loop-drafts",
      idbFactory,
      now: () => FINISHED_AT
    }));
    await draftService.save(SESSION_ID, FINAL_SOLUTION);

    const restartedDraftService = new LocalFinalSolutionDraftService(new IndexedDbFinalSolutionDraftRepository({
      databaseName: "problem-loop-drafts",
      idbFactory,
      now: () => SOLVED_AT
    }));
    await expect(restartedDraftService.read(SESSION_ID)).resolves.toMatchObject({ text: FINAL_SOLUTION });

    const solutionResult = await problemSession.saveSolution(FINAL_SOLUTION);
    expect(solutionResult).toMatchObject({
      commandStatus: "received",
      solution: {
        finalSolution: FINAL_SOLUTION,
        solutionAuthoredAt: SOLVED_AT.toISOString()
      },
      state: { activeSession: null }
    });
    await restartedDraftService.acknowledgeDeleted(SESSION_ID);
    await expect(restartedDraftService.read(SESSION_ID)).resolves.toBeNull();

    const solvedSession: ActiveProblemSessionPayload = {
      ...reviewableSession(),
      endedAt: FINISHED_AT.toISOString(),
      status: "SOLVED",
      updatedAt: SOLVED_AT.toISOString(),
      version: 2
    };
    const artifacts = capturedRecords.map((record) => ({
      artifactType: record.resourceType.toUpperCase(),
      hostname: record.hostname,
      id: record.id,
      occurredAt: record.capturedAt,
      title: record.title
    }));
    const summaryMarkup = renderToStaticMarkup(
      <ProblemSummary artifacts={artifacts} session={solvedSession} solution={FINAL_SOLUTION} />
    );

    expect(summaryMarkup.indexOf(FINAL_SOLUTION)).toBeLessThan(summaryMarkup.indexOf("Matching details"));
    expect(summaryMarkup).toContain("Your solution");
    expect(summaryMarkup).toContain("User-authored");
    expect(summaryMarkup).toContain("docs.spring.io");
    expect(summaryMarkup).not.toContain("Generated details loading");
    expect(summaryMarkup).not.toContain("token=secret");
    expect(summaryMarkup).not.toContain("utm_source");

    const opener = new RecordingReplayOpener();
    const replay = new ResourceReplayService(opener);
    await expect(
      replay.replay(
        capturedRecords.map((record) => ({ id: record.id, sanitizedUrl: record.canonicalUrl })),
        { confirmed: true }
      )
    ).resolves.toMatchObject({
      openedCount: 2,
      openedUrls: [
        "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors",
        "https://stackoverflow.com/questions/123/spring-security-cors"
      ],
      status: "opened"
    });
    expect(opener.openedUrls()).toEqual([
      "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html?topic=cors",
      "https://stackoverflow.com/questions/123/spring-security-cors"
    ]);
  });

  it("keeps offline final-solution drafts across restart and renders an offline summary without losing the exact solution", async () => {
    const idbFactory = new IDBFactory();
    const draftService = new LocalFinalSolutionDraftService(new IndexedDbFinalSolutionDraftRepository({
      databaseName: "problem-loop-offline-drafts",
      idbFactory,
      now: () => FINISHED_AT
    }));
    await draftService.save(SESSION_ID, FINAL_SOLUTION);

    const offlineProblemSession = createProblemSessionService({
      apiClient: new SequencedProblemSessionApiClient([{ status: "network_error" }]),
      initialState: storedRuntime(reviewableSession()),
      nowSequence: [SOLVED_AT]
    });

    await expect(offlineProblemSession.saveSolution(FINAL_SOLUTION)).resolves.toMatchObject({
      commandStatus: "network_error",
      state: { activeSession: { status: "REVIEWABLE" } }
    });

    const restartedDraftService = new LocalFinalSolutionDraftService(new IndexedDbFinalSolutionDraftRepository({
      databaseName: "problem-loop-offline-drafts",
      idbFactory,
      now: () => SOLVED_AT
    }));
    await expect(restartedDraftService.read(SESSION_ID)).resolves.toMatchObject({ text: FINAL_SOLUTION });

    const offlineSummaryMarkup = renderToStaticMarkup(
      <ProblemSummary initialStatus="offline" session={{ ...reviewableSession(), status: "SOLVED", version: 2 }} solution={FINAL_SOLUTION} />
    );
    expect(offlineSummaryMarkup).toContain(FINAL_SOLUTION);
    expect(offlineSummaryMarkup).toContain("Saved on this device. Trail will sync when online.");
    expect(offlineSummaryMarkup.indexOf(FINAL_SOLUTION)).toBeLessThan(offlineSummaryMarkup.indexOf("Matching details"));
    expect(offlineSummaryMarkup).not.toContain("Generated details loading");
  });

  it("reconciles offline queue and drafts across restart, reconnect, stale state, and sign-out warning", async () => {
    const idbFactory = new IDBFactory();
    const captureRuntime = createCaptureRuntime({
      idbFactory,
      queueOptions: {
        maxRecords: 2,
        now: sequencedDates([
          new Date("2026-07-16T08:02:00.000Z"),
          new Date("2026-07-16T08:03:00.000Z"),
          new Date("2026-07-16T08:04:00.000Z")
        ])
      }
    });
    await captureRuntime.consent.accept();

    await captureRuntime.capture.capture(navigationCandidate({ url: "https://example.test/oldest" }));
    await captureRuntime.capture.capture(navigationCandidate({ url: "https://example.test/middle" }));
    await captureRuntime.capture.capture(navigationCandidate({ url: "https://example.test/newest" }));
    await expect(captureRuntime.queue.read()).resolves.toEqual([
      expect.objectContaining({ canonicalUrl: "https://example.test/middle" }),
      expect.objectContaining({ canonicalUrl: "https://example.test/newest" })
    ]);

    const draftRepository = new IndexedDbFinalSolutionDraftRepository({
      databaseName: "problem-loop-reconciliation-drafts",
      idbFactory,
      maxRecords: 1,
      now: sequencedDates([FINISHED_AT, SOLVED_AT])
    });
    const draftService = new LocalFinalSolutionDraftService(draftRepository);
    await draftService.save("20000000-0000-4000-8000-000000000002", "Evictable stale draft.");
    await draftService.save(SESSION_ID, FINAL_SOLUTION);
    await expect(draftService.read("20000000-0000-4000-8000-000000000002")).resolves.toBeNull();
    await expect(draftService.read(SESSION_ID)).resolves.toMatchObject({ text: FINAL_SOLUTION });

    const staleProblemSession = createProblemSessionService({
      apiClient: new SequencedProblemSessionApiClient([{ status: "not_found" }]),
      initialState: storedRuntime(reviewableSession()),
      nowSequence: [SOLVED_AT]
    });
    await expect(staleProblemSession.saveSolution(FINAL_SOLUTION)).resolves.toMatchObject({
      commandStatus: "not_found",
      state: { activeSession: expect.objectContaining({ id: SESSION_ID, status: "REVIEWABLE" }) }
    });

    const signedOutProblemSession = new ProblemSessionRuntimeService({
      apiClient: new SequencedProblemSessionApiClient([]),
      authTokenProvider: new StaticAuthTokenProvider(null),
      idFactory: () => SESSION_ID,
      now: () => SOLVED_AT,
      storage: new InMemoryProblemSessionRuntimeStorage(storedRuntime(reviewableSession()))
    });
    await expect(signedOutProblemSession.saveSolution(FINAL_SOLUTION)).resolves.toMatchObject({
      commandStatus: "authentication_required",
      state: { activeSession: { status: "REVIEWABLE" } }
    });
    await expect(draftService.read(SESSION_ID)).resolves.toMatchObject({ text: FINAL_SOLUTION });

    const offlineSync = createSyncService({
      apiClient: new RecordingCaptureBatchApiClient({ status: "network_error" }),
      queue: captureRuntime.queue
    });
    await expect(offlineSync.synchronize()).resolves.toMatchObject({ status: "retryable_failure" });
    await expect(captureRuntime.queue.size()).resolves.toBe(2);

    const restartedCaptureRuntime = createCaptureRuntime({
      consentStorage: captureRuntime.consentStorage,
      controlStorage: captureRuntime.controlStorage,
      idbFactory,
      queueOptions: { maxRecords: 2 }
    });
    const reconnectApiClient = new RecordingCaptureBatchApiClient({
      status: "processed",
      response: {
        accepted: [{ clientEventId: "capture-record-2" }, { clientEventId: "capture-record-3" }],
        duplicate: [],
        rejected: []
      }
    });
    const reconnectSync = createSyncService({
      apiClient: reconnectApiClient,
      queue: restartedCaptureRuntime.queue
    });
    await expect(reconnectSync.synchronize()).resolves.toMatchObject({
      removedRecordIds: ["capture-record-2", "capture-record-3"],
      status: "processed"
    });
    expect(reconnectApiClient.commands()[0]?.request.events.map((event) => event.clientEventId)).toEqual([
      "capture-record-2",
      "capture-record-3"
    ]);
    await expect(restartedCaptureRuntime.queue.size()).resolves.toBe(0);
    await expect(draftService.read(SESSION_ID)).resolves.toMatchObject({ text: FINAL_SOLUTION });
  });
});

function createCaptureRuntime(options: Partial<CreateCaptureRuntimeOptions> = {}): CaptureRuntime {
  const idbFactory = options.idbFactory ?? new IDBFactory();
  const consentStorage = options.consentStorage ?? new InMemoryCaptureConsentStorage();
  const controlStorage = options.controlStorage ?? new InMemoryCaptureControlStorage();
  const queueOptions = {
    databaseName: "problem-loop-capture-queue",
    idbFactory,
    idFactory: options.idFactory ?? sequentialIds("capture-record"),
    now: options.queueOptions?.now ?? (() => CAPTURED_AT)
  };
  const queue = new IndexedDbCaptureEventQueue(
    options.queueOptions?.maxRecords === undefined
      ? queueOptions
      : { ...queueOptions, maxRecords: options.queueOptions.maxRecords }
  );
  const consent = new CaptureConsentRuntimeService({
    now: () => STARTED_AT,
    storage: consentStorage
  });
  const controls = new CaptureControlRuntimeService({
    now: () => STARTED_AT,
    storage: controlStorage
  });

  return {
    capture: new NavigationCaptureService({
      consentReader: consent,
      controlReader: controls,
      now: () => CAPTURED_AT,
      sanitizer: createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
      sink: queue
    }),
    consent,
    consentStorage,
    controlStorage,
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

function createProblemSessionService(options: {
  readonly apiClient: ProblemSessionCommandApiClient;
  readonly initialState?: StoredProblemSessionRuntime;
  readonly nowSequence: readonly Date[];
  readonly storage?: InMemoryProblemSessionRuntimeStorage;
}): ProblemSessionRuntimeService {
  const storage = options.storage ?? new InMemoryProblemSessionRuntimeStorage(options.initialState ?? null);
  const now = sequencedDates(options.nowSequence);
  return new ProblemSessionRuntimeService({
    apiClient: options.apiClient,
    authTokenProvider: new StaticAuthTokenProvider("access-token"),
    idFactory: () => SESSION_ID,
    now,
    storage
  });
}

function navigationCandidate(overrides: Partial<Parameters<NavigationCaptureService["capture"]>[0]> = {}): Parameters<NavigationCaptureService["capture"]>[0] {
  return {
    browserEventTime: 100,
    frameId: 0,
    navigationType: "link",
    openerTabId: null,
    source: "web_navigation_committed",
    tabId: 9,
    title: "Spring Security CORS",
    transitionQualifiers: ["from_address_bar"],
    url: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
    windowId: 1,
    ...overrides
  };
}

function commandProcessed(overrides: Partial<CommandProcessedOptions>): ProblemSessionCommandApiResult {
  const version = overrides.version ?? 0;
  return {
    etag: overrides.etag ?? `"${version}"`,
    response: {
      endedAt: overrides.endedAt ?? null,
      id: SESSION_ID,
      source: "MANUAL",
      startedAt: STARTED_AT.toISOString(),
      status: overrides.status ?? "CANDIDATE",
      title: "Spring Security CORS not working",
      updatedAt: overrides.updatedAt ?? STARTED_AT.toISOString(),
      version
    },
    status: "processed"
  };
}

function solutionProcessed(): ProblemSessionSolutionApiResult {
  return {
    etag: "\"2\"",
    response: {
      endedAt: FINISHED_AT.toISOString(),
      finalSolution: FINAL_SOLUTION,
      finalSolutionAuthor: "USER",
      id: SESSION_ID,
      solutionAuthoredAt: SOLVED_AT.toISOString(),
      source: "MANUAL",
      startedAt: STARTED_AT.toISOString(),
      status: "SOLVED",
      title: "Spring Security CORS not working",
      updatedAt: SOLVED_AT.toISOString(),
      version: 2
    },
    status: "processed"
  };
}

function reviewableSession(): ActiveProblemSessionPayload {
  return {
    detectionReasons: ["manual_session"],
    endedAt: FINISHED_AT.toISOString(),
    etag: "\"1\"",
    id: SESSION_ID,
    source: "MANUAL",
    startedAt: STARTED_AT.toISOString(),
    status: "REVIEWABLE",
    title: "Spring Security CORS not working",
    updatedAt: FINISHED_AT.toISOString(),
    version: 1
  };
}

function storedRuntime(activeSession: ActiveProblemSessionPayload): StoredProblemSessionRuntime {
  return {
    activeSession,
    dismissedSession: null,
    pendingStartId: null,
    schemaVersion: 1,
    updatedAt: FINISHED_AT.toISOString()
  };
}

function sequentialIds(prefix: string): () => string {
  let index = 0;
  return () => {
    index += 1;
    return `${prefix}-${index}`;
  };
}

function sequencedDates(dates: readonly Date[]): () => Date {
  const remainingDates = [...dates];
  return () => remainingDates.shift() ?? dates[dates.length - 1] ?? SOLVED_AT;
}

interface CreateCaptureRuntimeOptions {
  readonly consentStorage: InMemoryCaptureConsentStorage;
  readonly controlStorage: InMemoryCaptureControlStorage;
  readonly idbFactory: IDBFactory;
  readonly idFactory: () => string;
  readonly queueOptions: {
    readonly maxRecords?: number;
    readonly now?: () => Date;
  };
}

interface CaptureRuntime {
  readonly capture: NavigationCaptureService;
  readonly consent: CaptureConsentRuntimeService;
  readonly consentStorage: InMemoryCaptureConsentStorage;
  readonly controlStorage: InMemoryCaptureControlStorage;
  readonly queue: IndexedDbCaptureEventQueue;
}

interface CommandProcessedOptions {
  readonly endedAt: string | null;
  readonly etag: string;
  readonly status: "CANDIDATE" | "REVIEWABLE";
  readonly updatedAt: string;
  readonly version: number;
}

class SequencedProblemSessionApiClient implements ProblemSessionCommandApiClient {
  private readonly results: Array<ProblemSessionCommandApiResult | ProblemSessionSolutionApiResult>;

  constructor(results: Array<ProblemSessionCommandApiResult | ProblemSessionSolutionApiResult>) {
    this.results = [...results];
  }

  finish(_command: FinishProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    return Promise.resolve((this.results.shift() as ProblemSessionCommandApiResult | undefined) ?? { status: "network_error" });
  }

  saveSolution(_command: SaveProblemSessionSolutionCommand): Promise<ProblemSessionSolutionApiResult> {
    return Promise.resolve((this.results.shift() as ProblemSessionSolutionApiResult | undefined) ?? { status: "network_error" });
  }

  start(_command: StartProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    return Promise.resolve((this.results.shift() as ProblemSessionCommandApiResult | undefined) ?? { status: "network_error" });
  }
}

class StaticAuthTokenProvider {
  constructor(private readonly accessToken: string | null) {}

  getValidAccessToken(): Promise<string | null> {
    return Promise.resolve(this.accessToken);
  }
}

class RecordingReplayOpener implements ReplayResourceOpener {
  private readonly urls: string[] = [];

  open(url: string): Promise<void> {
    this.urls.push(url);
    return Promise.resolve();
  }

  openedUrls(): readonly string[] {
    return this.urls;
  }
}

class InMemoryProblemSessionRuntimeStorage implements ProblemSessionRuntimeStorage {
  private stored: StoredProblemSessionRuntime | null;

  constructor(initialState: StoredProblemSessionRuntime | null = null) {
    this.stored = initialState;
  }

  clear(): Promise<void> {
    this.stored = null;
    return Promise.resolve();
  }

  read(): Promise<StoredProblemSessionRuntime | null> {
    return Promise.resolve(this.stored);
  }

  write(state: StoredProblemSessionRuntime): Promise<void> {
    this.stored = state;
    return Promise.resolve();
  }
}

class InMemoryCaptureConsentStorage implements CaptureConsentStorage {
  private storedConsent: StoredCaptureConsent | null = null;

  clear(): Promise<void> {
    this.storedConsent = null;
    return Promise.resolve();
  }

  read(): Promise<StoredCaptureConsent | null> {
    return Promise.resolve(this.storedConsent);
  }

  write(consent: StoredCaptureConsent): Promise<void> {
    this.storedConsent = consent;
    return Promise.resolve();
  }
}

class InMemoryCaptureControlStorage implements CaptureControlStorage {
  private storedControls: StoredCaptureControls | null = null;

  clear(): Promise<void> {
    this.storedControls = null;
    return Promise.resolve();
  }

  read(): Promise<StoredCaptureControls | null> {
    return Promise.resolve(this.storedControls);
  }

  write(state: StoredCaptureControls): Promise<void> {
    this.storedControls = state;
    return Promise.resolve();
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
