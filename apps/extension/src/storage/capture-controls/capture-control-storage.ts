export const CAPTURE_CONTROL_STORAGE_KEY = "trail.capture.controls";
export const CAPTURE_CONTROL_SCHEMA_VERSION = 1;

export interface StoredDomainExclusion {
  readonly domain: string;
  readonly createdAt: string;
}

export interface StoredCaptureControls {
  readonly schemaVersion: typeof CAPTURE_CONTROL_SCHEMA_VERSION;
  readonly paused: boolean;
  readonly updatedAt: string;
  readonly excludedDomains: readonly StoredDomainExclusion[];
}

export interface CaptureControlStorage {
  read(): Promise<StoredCaptureControls | null>;
  write(state: StoredCaptureControls): Promise<void>;
  clear(): Promise<void>;
}

export class ChromeLocalCaptureControlStorage implements CaptureControlStorage {
  async read(): Promise<StoredCaptureControls | null> {
    const values = await chrome.storage.local.get(CAPTURE_CONTROL_STORAGE_KEY);
    return parseStoredCaptureControls(values[CAPTURE_CONTROL_STORAGE_KEY]);
  }

  async write(state: StoredCaptureControls): Promise<void> {
    await chrome.storage.local.set({ [CAPTURE_CONTROL_STORAGE_KEY]: toPersistableCaptureControls(state) });
  }

  async clear(): Promise<void> {
    await chrome.storage.local.remove(CAPTURE_CONTROL_STORAGE_KEY);
  }
}

export function createStoredCaptureControls(
  paused: boolean,
  excludedDomains: readonly StoredDomainExclusion[],
  updatedAt: Date
): StoredCaptureControls {
  return freezeStoredCaptureControls({
    schemaVersion: CAPTURE_CONTROL_SCHEMA_VERSION,
    paused,
    updatedAt: updatedAt.toISOString(),
    excludedDomains
  });
}

export function createDefaultStoredCaptureControls(now: Date): StoredCaptureControls {
  return createStoredCaptureControls(false, [], now);
}

export function parseStoredCaptureControls(value: unknown): StoredCaptureControls | null {
  if (!isRecord(value)) {
    return null;
  }

  if (
    value.schemaVersion !== CAPTURE_CONTROL_SCHEMA_VERSION ||
    typeof value.paused !== "boolean" ||
    typeof value.updatedAt !== "string" ||
    Number.isNaN(Date.parse(value.updatedAt)) ||
    !Array.isArray(value.excludedDomains)
  ) {
    return null;
  }

  const excludedDomains = value.excludedDomains.map(parseStoredDomainExclusion);
  if (excludedDomains.some((domain) => domain === null)) {
    return null;
  }

  return freezeStoredCaptureControls({
    schemaVersion: CAPTURE_CONTROL_SCHEMA_VERSION,
    paused: value.paused,
    updatedAt: value.updatedAt,
    excludedDomains: excludedDomains as StoredDomainExclusion[]
  });
}

export function freezeStoredCaptureControls(state: StoredCaptureControls): StoredCaptureControls {
  return Object.freeze({
    ...state,
    excludedDomains: Object.freeze(state.excludedDomains.map((exclusion) => Object.freeze({ ...exclusion })))
  });
}

function parseStoredDomainExclusion(value: unknown): StoredDomainExclusion | null {
  if (!isRecord(value)) {
    return null;
  }

  if (
    typeof value.domain !== "string" ||
    value.domain.trim().length === 0 ||
    value.domain !== value.domain.toLowerCase() ||
    typeof value.createdAt !== "string" ||
    Number.isNaN(Date.parse(value.createdAt))
  ) {
    return null;
  }

  return {
    domain: value.domain,
    createdAt: value.createdAt
  };
}

function toPersistableCaptureControls(state: StoredCaptureControls): StoredCaptureControls {
  return {
    ...state,
    excludedDomains: state.excludedDomains.map((exclusion) => ({ ...exclusion }))
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
