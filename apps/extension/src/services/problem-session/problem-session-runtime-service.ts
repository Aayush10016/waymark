import type {
  ActiveProblemSessionPayload,
  ProblemSessionCommandStatus,
  ProblemSessionReasonCategory,
  ProblemSessionRuntimePayload
} from "@trail/extension-messages";
import type {
  ProblemSessionCommandApiClient,
  ProblemSessionCommandApiResult,
  ProblemSessionCommandResponse,
  ProblemSessionSolutionApiResult
} from "./problem-session-command-api-client";
import {
  createDefaultProblemSessionRuntime,
  type ProblemSessionRuntimeStorage,
  type StoredProblemSessionRuntime
} from "../../storage/problem-session";

export interface ProblemSessionAuthTokenProvider {
  getValidAccessToken(): Promise<string | null>;
}

export interface ProblemSessionCaptureSynchronizer {
  synchronize(): Promise<unknown>;
}

export interface ProblemSessionRuntimeServiceOptions {
  readonly apiClient: ProblemSessionCommandApiClient;
  readonly authTokenProvider: ProblemSessionAuthTokenProvider;
  readonly captureSynchronizer?: ProblemSessionCaptureSynchronizer;
  readonly idFactory?: () => string;
  readonly now?: () => Date;
  readonly storage: ProblemSessionRuntimeStorage;
}

export interface ProblemSessionCommandResult {
  readonly commandStatus: ProblemSessionCommandStatus;
  readonly state: ProblemSessionRuntimePayload;
}

export interface ProblemSessionSolutionResult extends ProblemSessionCommandResult {
  readonly solution?: {
    readonly finalSolution: string;
    readonly solutionAuthoredAt: string;
  };
}

/**
 * Coordinates popup Problem Session commands without exposing tokens,
 * storage, or HTTP details to React surfaces.
 */
export class ProblemSessionRuntimeService {
  private static readonly DISMISS_UNDO_WINDOW_MILLISECONDS = 5_000;

  private readonly apiClient: ProblemSessionCommandApiClient;
  private readonly authTokenProvider: ProblemSessionAuthTokenProvider;
  private readonly captureSynchronizer: ProblemSessionCaptureSynchronizer | null;
  private readonly idFactory: () => string;
  private readonly now: () => Date;
  private readonly storage: ProblemSessionRuntimeStorage;

  private pendingStart: Promise<ProblemSessionCommandResult> | null = null;
  private pendingFinish: Promise<ProblemSessionCommandResult> | null = null;
  private pendingSolution: Promise<ProblemSessionSolutionResult> | null = null;

  constructor(options: ProblemSessionRuntimeServiceOptions) {
    this.apiClient = options.apiClient;
    this.authTokenProvider = options.authTokenProvider;
    this.captureSynchronizer = options.captureSynchronizer ?? null;
    this.idFactory = options.idFactory ?? (() => crypto.randomUUID());
    this.now = options.now ?? (() => new Date());
    this.storage = options.storage;
  }

  async readState(): Promise<ProblemSessionCommandResult> {
    return {
      commandStatus: "received",
      state: this.toPayload(await this.readRuntime())
    };
  }

  async start(title: string): Promise<ProblemSessionCommandResult> {
    if (this.pendingStart !== null) {
      return this.pendingStart;
    }

    this.pendingStart = this.performStart(title);

    try {
      return await this.pendingStart;
    } finally {
      this.pendingStart = null;
    }
  }

  async finish(): Promise<ProblemSessionCommandResult> {
    if (this.pendingFinish !== null) {
      return this.pendingFinish;
    }

    this.pendingFinish = this.performFinish();

    try {
      return await this.pendingFinish;
    } finally {
      this.pendingFinish = null;
    }
  }

  async dismiss(): Promise<ProblemSessionCommandResult> {
    const runtime = await this.readRuntime();

    if (runtime.activeSession === null) {
      return {
        commandStatus: "received",
        state: this.toPayload(runtime)
      };
    }

    const undoExpiresAt = new Date(
      this.now().getTime() +
        ProblemSessionRuntimeService.DISMISS_UNDO_WINDOW_MILLISECONDS
    );

    const updated = this.withUpdatedAt({
      ...runtime,
      activeSession: null,
      dismissedSession: {
        activeSession: runtime.activeSession,
        id: runtime.activeSession.id,
        undoExpiresAt: undoExpiresAt.toISOString()
      }
    });

    await this.storage.write(updated);

    return {
      commandStatus: "received",
      state: this.toPayload(updated)
    };
  }

  async saveSolution(
    finalSolution: string
  ): Promise<ProblemSessionSolutionResult> {
    if (this.pendingSolution !== null) {
      return this.pendingSolution;
    }

    this.pendingSolution = this.performSaveSolution(finalSolution);

    try {
      return await this.pendingSolution;
    } finally {
      this.pendingSolution = null;
    }
  }

  async undoDismiss(): Promise<ProblemSessionCommandResult> {
    const runtime = await this.readRuntime();

    if (
      runtime.dismissedSession === null ||
      runtime.activeSession !== null
    ) {
      return {
        commandStatus: "received",
        state: this.toPayload(runtime)
      };
    }

    const updated = this.withUpdatedAt({
      ...runtime,
      activeSession: runtime.dismissedSession.activeSession,
      dismissedSession: null
    });

    await this.storage.write(updated);

    return {
      commandStatus: "received",
      state: this.toPayload(updated)
    };
  }

  private async performStart(title: string): Promise<ProblemSessionCommandResult> {
    const runtime = await this.readRuntime();
    const normalizedTitle = title.replace(/\s+/g, " ").trim();

    if (normalizedTitle.length === 0) {
      return {
        commandStatus: "invalid_request",
        state: this.toPayload(runtime)
      };
    }

    if (runtime.activeSession !== null) {
      return {
        commandStatus: "received",
        state: this.toPayload(runtime)
      };
    }

    const accessToken =
      await this.authTokenProvider.getValidAccessToken();

    if (accessToken === null) {
      return {
        commandStatus: "authentication_required",
        state: this.toPayload(runtime)
      };
    }

    const problemSessionId =
      runtime.pendingStartId ?? this.idFactory();

    const runtimeWithPendingId =
      runtime.pendingStartId === problemSessionId
        ? runtime
        : this.withUpdatedAt({
            ...runtime,
            pendingStartId: problemSessionId
          });

    if (runtimeWithPendingId !== runtime) {
      await this.storage.write(runtimeWithPendingId);
    }

    const result = await this.apiClient.start({
      accessToken,
      request: {
        problemSessionId,
        title: normalizedTitle,
        startedAt: this.now().toISOString()
      }
    });

    return this.applyApiResult(result, runtimeWithPendingId, {
      clearPendingStartId: true
    });
  }

  private async performFinish(): Promise<ProblemSessionCommandResult> {
    const runtime = await this.readRuntime();

    if (runtime.activeSession === null) {
      return {
        commandStatus: "not_found",
        state: this.toPayload(runtime)
      };
    }

    const accessToken =
      await this.authTokenProvider.getValidAccessToken();

    if (accessToken === null) {
      return {
        commandStatus: "authentication_required",
        state: this.toPayload(runtime)
      };
    }

    const result = await this.apiClient.finish({
      accessToken,
      etag: runtime.activeSession.etag,
      problemSessionId: runtime.activeSession.id,
      request: {
        finishedAt: this.now().toISOString()
      }
    });

    return this.applyFinishApiResult(result, runtime);
  }

  private async performSaveSolution(
    finalSolution: string
  ): Promise<ProblemSessionSolutionResult> {
    const runtime = await this.readRuntime();

    if (runtime.activeSession === null) {
      return {
        commandStatus: "not_found",
        state: this.toPayload(runtime)
      };
    }

    const accessToken =
      await this.authTokenProvider.getValidAccessToken();

    if (accessToken === null) {
      return {
        commandStatus: "authentication_required",
        state: this.toPayload(runtime)
      };
    }

    await this.synchronizeCaptureQueueBeforeSolutionSave();

    const result = await this.apiClient.saveSolution({
      accessToken,
      etag: runtime.activeSession.etag,
      idempotencyKey: this.idFactory(),
      problemSessionId: runtime.activeSession.id,
      request: {
        finalSolution
      }
    });

    return this.applySolutionResult(result, runtime);
  }

  private async applyApiResult(
    result: ProblemSessionCommandApiResult,
    runtime: StoredProblemSessionRuntime,
    options: { readonly clearPendingStartId: boolean }
  ): Promise<ProblemSessionCommandResult> {
    if (result.status !== "processed") {
      return {
        commandStatus: mapApiFailure(result.status),
        state: this.toPayload(runtime)
      };
    }

    const updated = this.withUpdatedAt({
      ...runtime,
      activeSession: toActiveProblemSession(
        result.response,
        result.etag
      ),
      dismissedSession: null,
      pendingStartId: options.clearPendingStartId
        ? null
        : runtime.pendingStartId
    });

    await this.storage.write(updated);

    return {
      commandStatus: "received",
      state: this.toPayload(updated)
    };
  }

  /*
   * A successful Finish is different from Start.
   *
   * Start:
   *   activeSession -> newly created/active session
   *
   * Finish:
   *   activeSession -> null
   *
   * The old implementation reused applyApiResult(), which converted the
   * server's finished-session response back into activeSession. That caused
   * the popup to continue displaying "Finish problem".
   */
  private async applyFinishApiResult(
    result: ProblemSessionCommandApiResult,
    runtime: StoredProblemSessionRuntime
  ): Promise<ProblemSessionCommandResult> {
    if (result.status !== "processed") {
      return {
        commandStatus: mapApiFailure(result.status),
        state: this.toPayload(runtime)
      };
    }

    /*
     * Finish closes the capture boundary, but the finished session must remain
     * available locally for the end-session flow.
     *
     * The side panel needs this session to show:
     *   Did you solve it?
     *
     * The runtime payload currently carries the current Problem Session under
     * activeSession. The status distinguishes an actively capturing CANDIDATE
     * from a REVIEWABLE session waiting for outcome processing.
     */
    const updated = this.withUpdatedAt({
      ...runtime,
      activeSession: toActiveProblemSession(
        result.response,
        result.etag
      ),
      dismissedSession: null,
      pendingStartId: null
    });

    await this.storage.write(updated);

    return {
      commandStatus: "received",
      state: this.toPayload(updated)
    };
  }

  private async applySolutionResult(
    result: ProblemSessionSolutionApiResult,
    runtime: StoredProblemSessionRuntime
  ): Promise<ProblemSessionSolutionResult> {
    if (result.status !== "processed") {
      return {
        commandStatus: mapApiFailure(result.status),
        state: this.toPayload(runtime)
      };
    }

    /*
     * The backend has successfully persisted the final solution.
     * The end-session workflow is now complete, so the finished session can
     * leave local active state.
     */
    const updated = this.withUpdatedAt({
      ...runtime,
      activeSession: null,
      dismissedSession: null,
      pendingStartId: null
    });

    await this.storage.write(updated);

    return {
      commandStatus: "received",
      solution: {
        finalSolution: result.response.finalSolution,
        solutionAuthoredAt: result.response.solutionAuthoredAt
      },
      state: this.toPayload(updated)
    };
  }

  private async readRuntime(): Promise<StoredProblemSessionRuntime> {
    const now = this.now();

    const runtime =
      (await this.storage.read()) ??
      createDefaultProblemSessionRuntime(now);

    if (
      runtime.dismissedSession === null ||
      Date.parse(runtime.dismissedSession.undoExpiresAt) >
        now.getTime()
    ) {
      return runtime;
    }

    const updated = this.withUpdatedAt({
      ...runtime,
      dismissedSession: null
    });

    await this.storage.write(updated);

    return updated;
  }

  private toPayload(
    runtime: StoredProblemSessionRuntime
  ): ProblemSessionRuntimePayload {
    return Object.freeze({
      activeSession:
        runtime.activeSession === null
          ? null
          : Object.freeze({ ...runtime.activeSession }),
      dismissedSession:
        runtime.dismissedSession === null
          ? null
          : Object.freeze({
              id: runtime.dismissedSession.id,
              undoExpiresAt:
                runtime.dismissedSession.undoExpiresAt
            })
    });
  }

  private withUpdatedAt(
    runtime: StoredProblemSessionRuntime
  ): StoredProblemSessionRuntime {
    return {
      ...runtime,
      updatedAt: this.now().toISOString()
    };
  }

  private async synchronizeCaptureQueueBeforeSolutionSave(): Promise<void> {
    if (this.captureSynchronizer === null) {
      return;
    }

    try {
      await this.captureSynchronizer.synchronize();
    } catch {
      return;
    }
  }
}

function toActiveProblemSession(
  response: ProblemSessionCommandResponse,
  etag: string
): ActiveProblemSessionPayload {
  return Object.freeze({
    detectionReasons: Object.freeze(
      reasonCategoriesFor(response.source)
    ),
    endedAt: response.endedAt ?? null,
    etag,
    id: response.id,
    source: response.source,
    startedAt: response.startedAt,
    status: response.status,
    title: response.title?.trim() || "Technical problem",
    updatedAt: response.updatedAt,
    version: response.version
  });
}

function reasonCategoriesFor(
  source: ProblemSessionCommandResponse["source"]
): readonly ProblemSessionReasonCategory[] {
  return source === "MANUAL"
    ? ["manual_session"]
    : [
        "session_boundary",
        "repeated_activity",
        "multiple_related_sources"
      ];
}

function mapApiFailure(
  status: Exclude<
    ProblemSessionCommandApiResult["status"],
    "processed"
  >
): ProblemSessionCommandStatus {
  switch (status) {
    case "authentication_rejected":
      return "authentication_required";

    case "conflict":
      return "conflict";

    case "invalid_request":
      return "invalid_request";

    case "malformed_response":
      return "unavailable";

    case "network_error":
      return "network_error";

    case "not_found":
      return "not_found";

    case "precondition_required":
      return "precondition_required";

    case "retryable_failure":
      return "server_error";

    case "stale_version":
      return "stale_version";
  }
}
