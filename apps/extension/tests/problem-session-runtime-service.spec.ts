import { describe, expect, it } from "vitest";
import type { ActiveProblemSessionPayload } from "@trail/extension-messages";
import type {
  ProblemSessionCommandApiClient,
  ProblemSessionCommandApiResult,
  ProblemSessionSolutionApiResult,
  SaveProblemSessionSolutionCommand,
  StartProblemSessionCommand,
  FinishProblemSessionCommand,
  ProblemSessionCaptureSynchronizer
} from "../src/services/problem-session";
import { ProblemSessionRuntimeService } from "../src/services/problem-session";
import type {
  ProblemSessionRuntimeStorage,
  StoredProblemSessionRuntime
} from "../src/storage/problem-session/problem-session-storage";

const NOW = new Date("2026-07-16T08:00:00.000Z");
const LATER = new Date("2026-07-16T08:05:00.000Z");
const SESSION_ID = "10000000-0000-4000-8000-000000000001";

describe("ProblemSessionRuntimeService", () => {
  it("starts a manual Problem Session with a stable generated ID and stores authoritative server state", async () => {
    const apiClient = new SequencedProblemSessionApiClient([
      processed({
        id: SESSION_ID,
        status: "CANDIDATE",
        version: 0
      })
    ]);
    const storage = new InMemoryProblemSessionRuntimeStorage();
    const service = createService({ apiClient, storage });

    const result = await service.start("JWT authentication failing");

    expect(result).toMatchObject({
      commandStatus: "received",
      state: {
        activeSession: {
          detectionReasons: ["manual_session"],
          etag: "\"0\"",
          id: SESSION_ID,
          source: "MANUAL",
          status: "CANDIDATE",
          version: 0
        }
      }
    });
    expect(apiClient.startCommands()).toEqual([
      {
        accessToken: "access-token",
        request: {
          problemSessionId: SESSION_ID,
          startedAt: NOW.toISOString(),
          title: "JWT authentication failing"
        }
      }
    ]);
    expect(storage.snapshot()?.pendingStartId).toBeNull();
  });

  it("preserves a pending start ID across network failure to avoid duplicate sessions on retry", async () => {
    const apiClient = new SequencedProblemSessionApiClient([
      { status: "network_error" },
      processed({ id: SESSION_ID, status: "CANDIDATE", version: 0 })
    ]);
    const storage = new InMemoryProblemSessionRuntimeStorage();
    const service = createService({ apiClient, storage });

    await expect(service.start("JWT authentication failing")).resolves.toMatchObject({ commandStatus: "network_error" });
    await expect(service.start("JWT authentication failing")).resolves.toMatchObject({ commandStatus: "received" });

    expect(apiClient.startCommands().map((command) => command.request.problemSessionId)).toEqual([SESSION_ID, SESSION_ID]);
  });

  it("does not call the API when authentication is unavailable", async () => {
    const apiClient = new SequencedProblemSessionApiClient([]);
    const service = createService({ accessToken: null, apiClient });

    await expect(service.start("JWT authentication failing")).resolves.toMatchObject({ commandStatus: "authentication_required" });

    expect(apiClient.startCommands()).toEqual([]);
  });

  it("finishes with the stored ETag and stores the returned reviewable state", async () => {
    const apiClient = new SequencedProblemSessionApiClient([
      processed({ id: SESSION_ID, status: "REVIEWABLE", version: 1, etag: "\"1\"", endedAt: LATER.toISOString() })
    ]);
    const storage = new InMemoryProblemSessionRuntimeStorage(storedRuntime(activeSession()));
    const service = createService({ apiClient, now: () => LATER, storage });

    const result = await service.finish();

    expect(result.state.activeSession).toMatchObject({
      endedAt: LATER.toISOString(),
      etag: "\"1\"",
      id: SESSION_ID,
      status: "REVIEWABLE",
      version: 1
    });
    expect(apiClient.finishCommands()).toEqual([
      {
        accessToken: "access-token",
        etag: "\"0\"",
        problemSessionId: SESSION_ID,
        request: {
          finishedAt: LATER.toISOString()
        }
      }
    ]);
  });

  it("keeps local state unchanged on stale version and network failures", async () => {
    const apiClient = new SequencedProblemSessionApiClient([{ status: "stale_version" }, { status: "network_error" }]);
    const storage = new InMemoryProblemSessionRuntimeStorage(storedRuntime(activeSession()));
    const service = createService({ apiClient, storage });

    await expect(service.finish()).resolves.toMatchObject({ commandStatus: "stale_version" });
    await expect(service.finish()).resolves.toMatchObject({ commandStatus: "network_error" });

    expect(storage.snapshot()?.activeSession).toMatchObject({ id: SESSION_ID, status: "CANDIDATE", version: 0 });
  });

  it("saves a final solution through the authenticated API path and stores solved state", async () => {
    const apiClient = new SequencedProblemSessionApiClient([
      solutionProcessed({
        finalSolution: "Allowed localhost:3000 in CorsConfiguration.",
        id: SESSION_ID,
        status: "SOLVED",
        version: 2
      })
    ]);
    const storage = new InMemoryProblemSessionRuntimeStorage(storedRuntime({
      ...activeSession(),
      endedAt: LATER.toISOString(),
      etag: "\"1\"",
      status: "REVIEWABLE",
      version: 1
    }));
    const service = createService({ apiClient, storage });

    const result = await service.saveSolution("Allowed localhost:3000 in CorsConfiguration.");

    expect(result).toMatchObject({
      commandStatus: "received",
      solution: {
        finalSolution: "Allowed localhost:3000 in CorsConfiguration."
      },
      state: {
        activeSession: null
      }
    });
    expect(storage.snapshot()?.activeSession).toBeNull();
    expect(apiClient.saveSolutionCommands()).toEqual([
      {
        accessToken: "access-token",
        etag: "\"1\"",
        idempotencyKey: SESSION_ID,
        problemSessionId: SESSION_ID,
        request: {
          finalSolution: "Allowed localhost:3000 in CorsConfiguration."
        }
      }
    ]);
  });

  it("synchronizes queued captures before saving a final solution for artifact promotion", async () => {
    const events: string[] = [];
    const apiClient = new SequencedProblemSessionApiClient([
      solutionProcessed({
        finalSolution: "JWT on gpt",
        id: SESSION_ID,
        status: "SOLVED",
        version: 2
      })
    ], events);
    const captureSynchronizer = new RecordingProblemSessionCaptureSynchronizer(events);
    const storage = new InMemoryProblemSessionRuntimeStorage(storedRuntime({
      ...activeSession(),
      endedAt: LATER.toISOString(),
      etag: "\"1\"",
      status: "REVIEWABLE",
      version: 1
    }));
    const service = createService({ apiClient, captureSynchronizer, storage });

    await expect(service.saveSolution("JWT on gpt")).resolves.toMatchObject({ commandStatus: "received" });

    expect(captureSynchronizer.calls).toBe(1);
    expect(events).toEqual(["synchronize", "saveSolution"]);
  });

  it("dismisses Not Problem locally and restores with Undo inside the bounded window", async () => {
    const storage = new InMemoryProblemSessionRuntimeStorage(storedRuntime(activeSession()));
    const service = createService({ storage });

    const dismissed = await service.dismiss();
    expect(dismissed.state.activeSession).toBeNull();
    expect(dismissed.state.dismissedSession).toMatchObject({
      id: SESSION_ID,
      undoExpiresAt: "2026-07-16T08:00:05.000Z"
    });

    const restored = await service.undoDismiss();
    expect(restored.state.activeSession).toMatchObject({ id: SESSION_ID, status: "CANDIDATE" });
    expect(restored.state.dismissedSession).toBeNull();
  });

  it("expires stale undo state across service-worker restart", async () => {
    const expiredRuntime = storedRuntime(null, {
      activeSession: activeSession(),
      id: SESSION_ID,
      undoExpiresAt: "2026-07-16T07:59:59.000Z"
    });
    const storage = new InMemoryProblemSessionRuntimeStorage(expiredRuntime);
    const service = createService({ storage });

    await expect(service.readState()).resolves.toMatchObject({
      state: {
        activeSession: null,
        dismissedSession: null
      }
    });
  });
});

function createService(options: Partial<CreateServiceOptions> = {}): ProblemSessionRuntimeService {
  return new ProblemSessionRuntimeService({
    apiClient: options.apiClient ?? new SequencedProblemSessionApiClient([]),
    authTokenProvider: new StaticAuthTokenProvider(options.accessToken === undefined ? "access-token" : options.accessToken),
    ...(options.captureSynchronizer === undefined ? {} : { captureSynchronizer: options.captureSynchronizer }),
    idFactory: () => SESSION_ID,
    now: options.now ?? (() => NOW),
    storage: options.storage ?? new InMemoryProblemSessionRuntimeStorage()
  });
}

function processed(overrides: Partial<ProcessedOptions>): ProblemSessionCommandApiResult {
  const version = overrides.version ?? 0;
  return {
    etag: overrides.etag ?? `"${version}"`,
    response: {
      endedAt: overrides.endedAt ?? null,
      id: overrides.id ?? SESSION_ID,
      source: overrides.source ?? "MANUAL",
      startedAt: overrides.startedAt ?? NOW.toISOString(),
      status: overrides.status ?? "CANDIDATE",
      title: overrides.title ?? null,
      updatedAt: overrides.updatedAt ?? NOW.toISOString(),
      version
    },
    status: "processed"
  };
}

function solutionProcessed(overrides: Partial<SolutionProcessedOptions>): ProblemSessionSolutionApiResult {
  const version = overrides.version ?? 2;
  return {
    etag: overrides.etag ?? `"${version}"`,
    response: {
      endedAt: overrides.endedAt ?? LATER.toISOString(),
      finalSolution: overrides.finalSolution ?? "Solved.",
      finalSolutionAuthor: "USER",
      id: overrides.id ?? SESSION_ID,
      solutionAuthoredAt: overrides.solutionAuthoredAt ?? LATER.toISOString(),
      source: overrides.source ?? "MANUAL",
      startedAt: overrides.startedAt ?? NOW.toISOString(),
      status: overrides.status ?? "SOLVED",
      title: overrides.title ?? null,
      updatedAt: overrides.updatedAt ?? LATER.toISOString(),
      version
    },
    status: "processed"
  };
}

function activeSession(): ActiveProblemSessionPayload {
  return {
    detectionReasons: ["manual_session"],
    endedAt: null,
    etag: "\"0\"",
    id: SESSION_ID,
    source: "MANUAL",
    startedAt: NOW.toISOString(),
    status: "CANDIDATE",
    title: "JWT authentication failing",
    updatedAt: NOW.toISOString(),
    version: 0
  };
}

function storedRuntime(
  active: StoredProblemSessionRuntime["activeSession"],
  dismissed: StoredProblemSessionRuntime["dismissedSession"] = null
): StoredProblemSessionRuntime {
  return {
    activeSession: active,
    dismissedSession: dismissed,
    pendingStartId: null,
    schemaVersion: 1,
    updatedAt: NOW.toISOString()
  };
}

interface CreateServiceOptions {
  readonly accessToken: string | null;
  readonly apiClient: ProblemSessionCommandApiClient;
  readonly captureSynchronizer: ProblemSessionCaptureSynchronizer;
  readonly now: () => Date;
  readonly storage: ProblemSessionRuntimeStorage;
}

interface ProcessedOptions {
  readonly endedAt: string | null;
  readonly etag: string;
  readonly id: string;
  readonly source: "DETECTED" | "MANUAL";
  readonly startedAt: string;
  readonly status: "CANDIDATE" | "REVIEWABLE" | "SOLVED";
  readonly title: string | null;
  readonly updatedAt: string;
  readonly version: number;
}

interface SolutionProcessedOptions extends ProcessedOptions {
  readonly finalSolution: string;
  readonly solutionAuthoredAt: string;
  readonly status: "SOLVED";
}

class SequencedProblemSessionApiClient implements ProblemSessionCommandApiClient {
  private readonly finishCommandLog: FinishProblemSessionCommand[] = [];
  private readonly results: Array<ProblemSessionCommandApiResult | ProblemSessionSolutionApiResult>;
  private readonly saveSolutionCommandLog: SaveProblemSessionSolutionCommand[] = [];
  private readonly startCommandLog: StartProblemSessionCommand[] = [];

  constructor(
    results: Array<ProblemSessionCommandApiResult | ProblemSessionSolutionApiResult>,
    private readonly eventLog: string[] = []
  ) {
    this.results = [...results];
  }

  finish(command: FinishProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    this.eventLog.push("finish");
    this.finishCommandLog.push(command);
    return Promise.resolve(this.results.shift() ?? { status: "network_error" });
  }

  start(command: StartProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    this.eventLog.push("start");
    this.startCommandLog.push(command);
    return Promise.resolve(this.results.shift() ?? { status: "network_error" });
  }

  saveSolution(command: SaveProblemSessionSolutionCommand): Promise<ProblemSessionSolutionApiResult> {
    this.eventLog.push("saveSolution");
    this.saveSolutionCommandLog.push(command);
    return Promise.resolve((this.results.shift() as ProblemSessionSolutionApiResult | undefined) ?? { status: "network_error" });
  }

  finishCommands(): readonly FinishProblemSessionCommand[] {
    return this.finishCommandLog;
  }

  startCommands(): readonly StartProblemSessionCommand[] {
    return this.startCommandLog;
  }

  saveSolutionCommands(): readonly SaveProblemSessionSolutionCommand[] {
    return this.saveSolutionCommandLog;
  }

  events(): readonly string[] {
    return this.eventLog;
  }
}

class RecordingProblemSessionCaptureSynchronizer implements ProblemSessionCaptureSynchronizer {
  calls = 0;

  constructor(private readonly eventLog: string[] = []) {}

  synchronize(): Promise<void> {
    this.calls += 1;
    this.eventLog.push("synchronize");
    return Promise.resolve();
  }
}

class StaticAuthTokenProvider {
  constructor(private readonly accessToken: string | null) {}

  getValidAccessToken(): Promise<string | null> {
    return Promise.resolve(this.accessToken);
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

  snapshot(): StoredProblemSessionRuntime | null {
    return this.stored;
  }
}
