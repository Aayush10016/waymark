import { AUTH_STATUS, CAPTURE_STATE_MESSAGE_TYPES, type CaptureStatePayload } from "@trail/extension-messages";
import { describe, expect, it } from "vitest";
import type { AuthState } from "../src/features/auth";
import { CaptureControlRuntimeService } from "../src/services/capture-controls";
import {
  CaptureStateService,
  derivePopupPriorityState,
  handleCaptureStateMessage,
  type CapturePermissionReader,
  type CapturePermissionState,
  type CaptureStateAuthReader,
  type CaptureStateConsentReader,
  type CurrentTabReader,
  type NetworkStateReader
} from "../src/services/capture-state";
import type { CaptureConsentState } from "../src/services/consent";
import type { CaptureQueueStatistics, CaptureEventQueueRepository, StoredCaptureQueueRecord } from "../src/storage/capture-queue";
import type { CaptureControlStorage, StoredCaptureControls } from "../src/storage/capture-controls";

const NOW = new Date("2026-07-13T08:00:00.000Z");

describe("CaptureStateService", () => {
  it("retrieves content-safe runtime state with queue statistics", async () => {
    const service = createService();

    await expect(service.readState()).resolves.toMatchObject({
      authStatus: AUTH_STATUS.authenticated,
      consentStatus: "accepted",
      controls: {
        currentDomain: "docs.example.test",
        currentDomainExcluded: false,
        excludedDomainCount: 0,
        paused: false
      },
      network: { online: true },
      permissions: { missing: [], status: "granted" },
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
      sync: { status: "idle" }
    });
  });

  it("exposes pause and resume state changes", async () => {
    const service = createService();

    await expect(service.pause()).resolves.toMatchObject({
      controls: { paused: true },
      popup: { state: "paused" }
    });
    await expect(service.resume()).resolves.toMatchObject({
      controls: { paused: false },
      popup: { state: "recording_ready" }
    });
  });

  it("exposes consent and permission blockers through the popup priority model", async () => {
    await expect(createService({ consentState: missingConsentState() }).readState()).resolves.toMatchObject({
      consentStatus: "missing",
      popup: { state: "consent_required" }
    });

    await expect(createService({ permissionState: { status: "missing", missing: ["webNavigation"] } }).readState()).resolves.toMatchObject({
      permissions: { status: "missing", missing: ["webNavigation"] },
      popup: { state: "permission_missing" }
    });
  });

  it("exposes auth and offline states without raw activity", async () => {
    await expect(createService({ authState: { status: AUTH_STATUS.signedOut } }).readState()).resolves.toMatchObject({
      authStatus: AUTH_STATUS.signedOut,
      popup: { state: "auth_required" }
    });

    const offlineState = await createService({ online: false }).readState();
    expect(offlineState).toMatchObject({
      network: { online: false },
      popup: { state: "offline" },
      sync: { status: "offline" }
    });
    expect(JSON.stringify(offlineState)).not.toContain("https://");
  });

  it("exposes current-domain exclusion state and undo support", async () => {
    const service = createService();

    await expect(service.excludeCurrentDomain()).resolves.toMatchObject({
      controls: {
        currentDomain: "docs.example.test",
        currentDomainExcluded: true,
        excludedDomainCount: 1
      },
      popup: { state: "domain_excluded" }
    });

    await expect(service.undoDomainExclusion()).resolves.toMatchObject({
      controls: {
        currentDomainExcluded: false,
        excludedDomainCount: 0
      }
    });
  });

  it("survives service-worker restart by reading persisted control state", async () => {
    const controlStorage = new InMemoryCaptureControlStorage();
    await createService({ controlStorage }).excludeCurrentDomain();

    const restartedService = createService({ controlStorage });

    await expect(restartedService.readState()).resolves.toMatchObject({
      controls: {
        currentDomain: "docs.example.test",
        currentDomainExcluded: true,
        excludedDomainCount: 1
      }
    });
  });

  it("handles validated capture-state messages", async () => {
    const service = createService();
    const getResult = await handleCaptureStateMessage(service, message(CAPTURE_STATE_MESSAGE_TYPES.get));
    const pauseResult = await handleCaptureStateMessage(service, message(CAPTURE_STATE_MESSAGE_TYPES.pause));

    expect(getResult.handled).toBe(true);
    expect((getResult.response as { readonly payload: CaptureStatePayload }).payload.popup.state).toBe("recording_ready");
    expect(pauseResult.handled).toBe(true);
    expect((pauseResult.response as { readonly payload: CaptureStatePayload }).payload.popup.state).toBe("paused");
  });

  it("rejects malformed capture-state messages", async () => {
    await expect(handleCaptureStateMessage(createService(), { type: CAPTURE_STATE_MESSAGE_TYPES.get })).resolves.toEqual({
      handled: false,
      response: { errorCode: "INVALID_MESSAGE_SCHEMA" }
    });
  });

  it("keeps popup priority deterministic", () => {
    expect(
      derivePopupPriorityState({
        authStatus: AUTH_STATUS.signedOut,
        consentStatus: "accepted",
        currentDomainExcluded: true,
        online: false,
        paused: true,
        permissionState: { status: "missing", missing: ["tabs"] }
      }).state
    ).toBe("auth_required");

    expect(
      derivePopupPriorityState({
        authStatus: AUTH_STATUS.authenticated,
        consentStatus: "accepted",
        currentDomainExcluded: false,
        online: true,
        paused: true,
        permissionState: { status: "granted", missing: [] }
      }).state
    ).toBe("paused");
  });
});

function createService(options: Partial<CreateServiceOptions> = {}): CaptureStateService {
  return new CaptureStateService({
    authReader: new StaticAuthReader(options.authState ?? authenticatedState()),
    consentReader: new StaticConsentReader(options.consentState ?? acceptedConsentState()),
    controlService: new CaptureControlRuntimeService({
      now: () => NOW,
      storage: options.controlStorage ?? new InMemoryCaptureControlStorage()
    }),
    currentTabReader: new StaticCurrentTabReader(options.currentTabUrl ?? "https://docs.example.test/reference"),
    networkStateReader: new StaticNetworkStateReader(options.online ?? true),
    permissionReader: new StaticPermissionReader(options.permissionState ?? { status: "granted", missing: [] }),
    queue: options.queue ?? new StaticQueueRepository()
  });
}

function message(type: (typeof CAPTURE_STATE_MESSAGE_TYPES)[keyof typeof CAPTURE_STATE_MESSAGE_TYPES]) {
  return {
    schemaVersion: 1,
    type,
    requestId: "capture-state-1",
    source: "popup",
    payload: {}
  };
}

interface CreateServiceOptions {
  readonly authState: AuthState;
  readonly consentState: CaptureConsentState;
  readonly controlStorage: CaptureControlStorage;
  readonly currentTabUrl: string | null;
  readonly online: boolean;
  readonly permissionState: CapturePermissionState;
  readonly queue: CaptureEventQueueRepository;
}

class StaticAuthReader implements CaptureStateAuthReader {
  constructor(private readonly state: AuthState) {}

  status(): Promise<AuthState> {
    return Promise.resolve(this.state);
  }
}

class StaticConsentReader implements CaptureStateConsentReader {
  constructor(private readonly state: CaptureConsentState) {}

  readState(): Promise<CaptureConsentState> {
    return Promise.resolve(this.state);
  }
}

class StaticCurrentTabReader implements CurrentTabReader {
  constructor(private readonly url: string | null) {}

  read(): Promise<{ readonly url: string | null }> {
    return Promise.resolve({ url: this.url });
  }
}

class StaticNetworkStateReader implements NetworkStateReader {
  constructor(private readonly isOnline: boolean) {}

  online(): boolean {
    return this.isOnline;
  }
}

class StaticPermissionReader implements CapturePermissionReader {
  constructor(private readonly state: CapturePermissionState) {}

  read(): Promise<CapturePermissionState> {
    return Promise.resolve(this.state);
  }
}

class StaticQueueRepository implements CaptureEventQueueRepository {
  statistics(): Promise<CaptureQueueStatistics> {
    return Promise.resolve({
      maxBatchSize: 50,
      maxRecords: 1_000,
      newestEnqueuedAt: null,
      oldestEnqueuedAt: null,
      remainingCapacity: 1_000,
      totalRecords: 0
    });
  }

  append(): Promise<StoredCaptureQueueRecord> {
    return Promise.reject(new Error("append is not used by capture state."));
  }

  write(): Promise<never> {
    return Promise.reject(new Error("write is not used by capture state."));
  }

  read(): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.reject(new Error("read is not used by capture state."));
  }

  selectBatch(): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.reject(new Error("selectBatch is not used by capture state."));
  }

  remove(): Promise<void> {
    return Promise.reject(new Error("remove is not used by capture state."));
  }

  clear(): Promise<void> {
    return Promise.reject(new Error("clear is not used by capture state."));
  }

  size(): Promise<number> {
    return Promise.reject(new Error("size is not used by capture state."));
  }
}

class InMemoryCaptureControlStorage implements CaptureControlStorage {
  private storedState: StoredCaptureControls | null = null;

  read(): Promise<StoredCaptureControls | null> {
    return Promise.resolve(this.storedState);
  }

  write(state: StoredCaptureControls): Promise<void> {
    this.storedState = state;
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.storedState = null;
    return Promise.resolve();
  }
}

function authenticatedState(): AuthState {
  return {
    status: AUTH_STATUS.authenticated,
    accessTokenExpiresAt: "2026-07-13T09:00:00.000Z",
    refreshTokenExpiresAt: "2026-08-13T09:00:00.000Z"
  };
}

function acceptedConsentState(): CaptureConsentState {
  return {
    accepted: true,
    consentVersion: "capture-consent-v1",
    decidedAt: "2026-07-13T07:00:00.000Z",
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
