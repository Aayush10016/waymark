import { AUTH_STATUS, type AuthStatus, type CaptureStatePayload, type PopupPriorityState } from "@trail/extension-messages";
import type { AuthState } from "../../features/auth";
import type { CaptureConsentState } from "../consent";
import type { CaptureControlRuntimeService } from "../capture-controls";
import type { CaptureEventQueueRepository, CaptureQueueStatistics } from "../../storage/capture-queue";

const REQUIRED_CAPTURE_PERMISSIONS = ["storage", "tabs", "webNavigation"] as const;

export interface CaptureStateAuthReader {
  status(): Promise<AuthState>;
}

export interface CaptureStateConsentReader {
  readState(): Promise<CaptureConsentState>;
}

export interface CapturePermissionState {
  readonly missing: readonly string[];
  readonly status: "granted" | "missing";
}

export interface CapturePermissionReader {
  read(): Promise<CapturePermissionState>;
}

export interface CurrentTabSnapshot {
  readonly url: string | null;
}

export interface CurrentTabReader {
  read(): Promise<CurrentTabSnapshot>;
}

export interface NetworkStateReader {
  online(): boolean;
}

export interface CaptureStateServiceOptions {
  readonly authReader: CaptureStateAuthReader;
  readonly consentReader: CaptureStateConsentReader;
  readonly controlService: CaptureControlRuntimeService;
  readonly currentTabReader: CurrentTabReader;
  readonly networkStateReader: NetworkStateReader;
  readonly permissionReader: CapturePermissionReader;
  readonly queue: CaptureEventQueueRepository;
}

/**
 * Builds the content-safe runtime capture state consumed by extension surfaces.
 */
export class CaptureStateService {
  private readonly authReader: CaptureStateAuthReader;
  private readonly consentReader: CaptureStateConsentReader;
  private readonly controlService: CaptureControlRuntimeService;
  private readonly currentTabReader: CurrentTabReader;
  private readonly networkStateReader: NetworkStateReader;
  private readonly permissionReader: CapturePermissionReader;
  private readonly queue: CaptureEventQueueRepository;

  constructor(options: CaptureStateServiceOptions) {
    this.authReader = options.authReader;
    this.consentReader = options.consentReader;
    this.controlService = options.controlService;
    this.currentTabReader = options.currentTabReader;
    this.networkStateReader = options.networkStateReader;
    this.permissionReader = options.permissionReader;
    this.queue = options.queue;
  }

  async readState(): Promise<CaptureStatePayload> {
    const [authState, consentState, controlState, permissionState, queueStatistics, currentTab] = await Promise.all([
      this.authReader.status(),
      this.consentReader.readState(),
      this.controlService.readState(),
      this.permissionReader.read(),
      this.queue.statistics(),
      this.currentTabReader.read()
    ]);
    const online = this.networkStateReader.online();
    const currentDomain = currentTab.url === null ? null : parseHttpDomain(currentTab.url);
    const controlDecision = currentTab.url === null ? null : await this.controlService.evaluateUrl(currentTab.url);
    const currentDomainExcluded = controlDecision?.status === "blocked" && controlDecision.reason === "domain_excluded";

    return toCaptureStatePayload({
      authStatus: authState.status,
      consentStatus: consentState.status,
      currentDomain,
      currentDomainExcluded,
      excludedDomainCount: controlState.excludedDomains.length,
      online,
      paused: controlState.paused,
      permissionState,
      queueStatistics
    });
  }

  async pause(): Promise<CaptureStatePayload> {
    await this.controlService.pause();
    return this.readState();
  }

  async resume(): Promise<CaptureStatePayload> {
    await this.controlService.resume();
    return this.readState();
  }

  async excludeCurrentDomain(): Promise<CaptureStatePayload> {
    const currentTab = await this.currentTabReader.read();
    if (currentTab.url !== null) {
      await this.controlService.excludeCurrentDomain(currentTab.url);
    }
    return this.readState();
  }

  async undoDomainExclusion(): Promise<CaptureStatePayload> {
    await this.controlService.undoLastDomainExclusion();
    return this.readState();
  }
}

export class BrowserPermissionReader implements CapturePermissionReader {
  private readonly api: typeof chrome;

  constructor(api: typeof chrome) {
    this.api = api;
  }

  async read(): Promise<CapturePermissionState> {
    if (this.api.permissions === undefined) {
      return { status: "granted", missing: [] };
    }

    const missing: string[] = [];
    for (const permission of REQUIRED_CAPTURE_PERMISSIONS) {
      const granted = await this.contains(permission);
      if (!granted) {
        missing.push(permission);
      }
    }

    return {
      status: missing.length === 0 ? "granted" : "missing",
      missing
    };
  }

  private contains(permission: string): Promise<boolean> {
    return new Promise((resolve) => {
      this.api.permissions.contains({ permissions: [permission as chrome.runtime.ManifestPermissions] }, (granted) => {
        resolve(Boolean(granted));
      });
    });
  }
}

export class BrowserCurrentTabReader implements CurrentTabReader {
  private readonly api: typeof chrome;

  constructor(api: typeof chrome) {
    this.api = api;
  }

  read(): Promise<CurrentTabSnapshot> {
    return new Promise((resolve) => {
      this.api.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        const activeTab = tabs[0];
        resolve({ url: activeTab?.url ?? null });
      });
    });
  }
}

export class BrowserNetworkStateReader implements NetworkStateReader {
  online(): boolean {
    return navigator.onLine;
  }
}

export function toCaptureStatePayload(options: {
  readonly authStatus: AuthStatus;
  readonly consentStatus: CaptureConsentState["status"];
  readonly currentDomain: string | null;
  readonly currentDomainExcluded: boolean;
  readonly excludedDomainCount: number;
  readonly online: boolean;
  readonly paused: boolean;
  readonly permissionState: CapturePermissionState;
  readonly queueStatistics: CaptureQueueStatistics;
}): CaptureStatePayload {
  const popup = derivePopupPriorityState(options);
  return Object.freeze({
    authStatus: options.authStatus,
    consentStatus: options.consentStatus,
    controls: Object.freeze({
      currentDomain: options.currentDomain,
      currentDomainExcluded: options.currentDomainExcluded,
      excludedDomainCount: options.excludedDomainCount,
      paused: options.paused
    }),
    network: Object.freeze({
      online: options.online
    }),
    permissions: Object.freeze({
      missing: Object.freeze([...options.permissionState.missing]),
      status: options.permissionState.status
    }),
    popup: Object.freeze(popup),
    queue: Object.freeze({
      maxBatchSize: options.queueStatistics.maxBatchSize,
      maxRecords: options.queueStatistics.maxRecords,
      oldestEnqueuedAt: options.queueStatistics.oldestEnqueuedAt,
      remainingCapacity: options.queueStatistics.remainingCapacity,
      totalRecords: options.queueStatistics.totalRecords
    }),
    sync: Object.freeze({
      status: options.online ? "idle" : "offline"
    })
  });
}

export function derivePopupPriorityState(options: {
  readonly authStatus: AuthStatus;
  readonly consentStatus: CaptureConsentState["status"];
  readonly currentDomainExcluded: boolean;
  readonly online: boolean;
  readonly paused: boolean;
  readonly permissionState: CapturePermissionState;
}): CaptureStatePayload["popup"] {
  if (options.authStatus !== AUTH_STATUS.authenticated) {
    return popupState("auth_required", "Reconnect Trail", "open_onboarding");
  }

  if (options.consentStatus !== "accepted") {
    return popupState("consent_required", "Capture is not enabled", "open_onboarding");
  }

  if (options.permissionState.status === "missing") {
    return popupState("permission_missing", "Permission needed", "restore_permission");
  }

  if (options.currentDomainExcluded) {
    return popupState("domain_excluded", "Domain blocked", "open_trail");
  }

  if (options.paused) {
    return popupState("paused", "Trail is paused", "resume_capture");
  }

  if (!options.online) {
    return popupState("offline", "Recording on this device", "open_trail");
  }

  return popupState("recording_ready", "Recording quietly", "open_trail");
}

function popupState(
  state: PopupPriorityState,
  statusLabel: string,
  primaryAction: CaptureStatePayload["popup"]["primaryAction"]
): CaptureStatePayload["popup"] {
  return {
    primaryAction,
    state,
    statusLabel
  };
}

function parseHttpDomain(url: string): string | null {
  try {
    const parsedUrl = new URL(url);
    if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
      return null;
    }

    return parsedUrl.hostname.toLowerCase().replace(/\.$/, "");
  } catch {
    return null;
  }
}
