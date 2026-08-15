import { describe, expect, it } from "vitest";
import {
  canCaptureWithConsent,
  CaptureConsentRuntimeService,
  type CaptureConsentState
} from "../src/services/consent";
import {
  CAPTURE_CONSENT_SCHEMA_VERSION,
  CURRENT_CAPTURE_CONSENT_VERSION,
  parseStoredCaptureConsent,
  type CaptureConsentStorage,
  type StoredCaptureConsent
} from "../src/storage/consent/capture-consent-storage";

const DECIDED_AT = new Date("2026-07-12T09:30:00.000Z");

describe("CaptureConsentRuntimeService", () => {
  it("starts missing on first launch and keeps capture disabled", async () => {
    const service = new CaptureConsentRuntimeService({
      storage: new InMemoryCaptureConsentStorage(),
      now: () => DECIDED_AT
    });

    await expect(service.readState()).resolves.toEqual({
      status: "missing",
      accepted: false,
      consentVersion: null,
      decidedAt: null
    });
    expect(canCaptureWithConsent(service.current())).toBe(false);
  });

  it("persists explicit consent with version, timestamp, and accepted flag", async () => {
    const storage = new InMemoryCaptureConsentStorage();
    const service = new CaptureConsentRuntimeService({ storage, now: () => DECIDED_AT });

    await expect(service.accept()).resolves.toEqual({
      status: "accepted",
      accepted: true,
      consentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
      decidedAt: DECIDED_AT.toISOString()
    });

    expect(storage.snapshot()).toEqual({
      schemaVersion: CAPTURE_CONSENT_SCHEMA_VERSION,
      consentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
      accepted: true,
      decidedAt: DECIDED_AT.toISOString()
    });
    expect(canCaptureWithConsent(service.current())).toBe(true);
  });

  it("persists decline and keeps capture disabled", async () => {
    const storage = new InMemoryCaptureConsentStorage();
    const service = new CaptureConsentRuntimeService({ storage, now: () => DECIDED_AT });

    await expect(service.decline()).resolves.toEqual({
      status: "declined",
      accepted: false,
      consentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
      decidedAt: DECIDED_AT.toISOString()
    });

    expect(storage.snapshot()?.accepted).toBe(false);
    expect(canCaptureWithConsent(service.current())).toBe(false);
  });

  it("survives restart by reading persisted consent", async () => {
    const storage = new InMemoryCaptureConsentStorage();
    const firstRuntime = new CaptureConsentRuntimeService({ storage, now: () => DECIDED_AT });
    await firstRuntime.accept();

    const restartedRuntime = new CaptureConsentRuntimeService({ storage, now: () => new Date("2026-07-12T10:00:00.000Z") });

    await expect(restartedRuntime.readState()).resolves.toEqual({
      status: "accepted",
      accepted: true,
      consentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
      decidedAt: DECIDED_AT.toISOString()
    });
    expect(canCaptureWithConsent(restartedRuntime.current())).toBe(true);
  });

  it("supports revocation and updates runtime state immediately", async () => {
    const service = new CaptureConsentRuntimeService({
      storage: new InMemoryCaptureConsentStorage(),
      now: () => DECIDED_AT
    });
    const observedStates: CaptureConsentState[] = [];
    service.subscribe((state) => observedStates.push(state));

    await service.accept();
    await service.revoke();

    expect(observedStates.map((state) => state.status)).toEqual(["accepted", "declined"]);
    expect(service.current().status).toBe("declined");
    expect(canCaptureWithConsent(service.current())).toBe(false);
  });

  it("treats version mismatch as stale and disables capture", async () => {
    const storage = new InMemoryCaptureConsentStorage({
      schemaVersion: CAPTURE_CONSENT_SCHEMA_VERSION,
      consentVersion: "capture-consent-v0",
      accepted: true,
      decidedAt: DECIDED_AT.toISOString()
    });
    const service = new CaptureConsentRuntimeService({ storage });

    await expect(service.readState()).resolves.toEqual({
      status: "stale",
      accepted: false,
      consentVersion: "capture-consent-v0",
      requiredConsentVersion: CURRENT_CAPTURE_CONSENT_VERSION,
      decidedAt: DECIDED_AT.toISOString()
    });
    expect(canCaptureWithConsent(service.current())).toBe(false);
  });

  it("handles unsafe stored shapes as missing for migration safety", () => {
    expect(parseStoredCaptureConsent({ schemaVersion: 999, accepted: true })).toBeNull();
    expect(parseStoredCaptureConsent({ schemaVersion: 1, consentVersion: "capture-consent-v1" })).toBeNull();
    expect(parseStoredCaptureConsent({ schemaVersion: 1, consentVersion: "capture-consent-v1", accepted: true, decidedAt: "bad-date" })).toBeNull();
  });
});

class InMemoryCaptureConsentStorage implements CaptureConsentStorage {
  private storedConsent: StoredCaptureConsent | null;

  constructor(initialConsent: StoredCaptureConsent | null = null) {
    this.storedConsent = initialConsent;
  }

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

  snapshot(): StoredCaptureConsent | null {
    return this.storedConsent;
  }
}
