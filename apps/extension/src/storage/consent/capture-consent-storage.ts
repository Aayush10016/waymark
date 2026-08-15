export const CAPTURE_CONSENT_STORAGE_KEY = "trail.capture.consent";
export const CURRENT_CAPTURE_CONSENT_VERSION = "capture-consent-v1";
export const CAPTURE_CONSENT_SCHEMA_VERSION = 1;

export interface StoredCaptureConsent {
  readonly schemaVersion: typeof CAPTURE_CONSENT_SCHEMA_VERSION;
  readonly consentVersion: string;
  readonly accepted: boolean;
  readonly decidedAt: string;
}

export interface CaptureConsentStorage {
  read(): Promise<StoredCaptureConsent | null>;
  write(consent: StoredCaptureConsent): Promise<void>;
  clear(): Promise<void>;
}

export class ChromeLocalCaptureConsentStorage implements CaptureConsentStorage {
  async read(): Promise<StoredCaptureConsent | null> {
    const values = await chrome.storage.local.get(CAPTURE_CONSENT_STORAGE_KEY);
    return parseStoredCaptureConsent(values[CAPTURE_CONSENT_STORAGE_KEY]);
  }

  async write(consent: StoredCaptureConsent): Promise<void> {
    await chrome.storage.local.set({ [CAPTURE_CONSENT_STORAGE_KEY]: consent });
  }

  async clear(): Promise<void> {
    await chrome.storage.local.remove(CAPTURE_CONSENT_STORAGE_KEY);
  }
}

export function parseStoredCaptureConsent(value: unknown): StoredCaptureConsent | null {
  if (!isRecord(value)) {
    return null;
  }

  if (
    value.schemaVersion !== CAPTURE_CONSENT_SCHEMA_VERSION ||
    typeof value.consentVersion !== "string" ||
    typeof value.accepted !== "boolean" ||
    typeof value.decidedAt !== "string" ||
    Number.isNaN(Date.parse(value.decidedAt))
  ) {
    return null;
  }

  return {
    schemaVersion: CAPTURE_CONSENT_SCHEMA_VERSION,
    consentVersion: value.consentVersion,
    accepted: value.accepted,
    decidedAt: value.decidedAt
  };
}

export function createStoredCaptureConsent(accepted: boolean, decidedAt: Date): StoredCaptureConsent {
  return {
    schemaVersion: CAPTURE_CONSENT_SCHEMA_VERSION,
    consentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
    accepted,
    decidedAt: decidedAt.toISOString()
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
