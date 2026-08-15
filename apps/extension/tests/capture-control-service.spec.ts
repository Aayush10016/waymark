import { describe, expect, it } from "vitest";
import {
  CaptureControlRuntimeService,
  isCaptureAllowedByControls,
  type CaptureControlListener
} from "../src/services/capture-controls";
import {
  CAPTURE_CONTROL_SCHEMA_VERSION,
  createStoredCaptureControls,
  parseStoredCaptureControls,
  type CaptureControlStorage,
  type StoredCaptureControls
} from "../src/storage/capture-controls";

const UPDATED_AT = new Date("2026-07-12T12:00:00.000Z");

describe("CaptureControlRuntimeService", () => {
  it("starts unpaused with no exclusions on first launch", async () => {
    const service = createService();

    await expect(service.readState()).resolves.toEqual({
      schemaVersion: CAPTURE_CONTROL_SCHEMA_VERSION,
      paused: false,
      updatedAt: UPDATED_AT.toISOString(),
      excludedDomains: []
    });

    await expect(service.evaluateUrl("https://example.test/docs")).resolves.toEqual({ status: "allowed" });
  });

  it("persists pause and resume state", async () => {
    const storage = new InMemoryCaptureControlStorage();
    const service = createService({ storage });

    await expect(service.pause()).resolves.toMatchObject({ paused: true });
    expect(storage.snapshot()?.paused).toBe(true);
    await expect(service.evaluateUrl("https://example.test/docs")).resolves.toEqual({ status: "blocked", reason: "paused" });

    await expect(service.resume()).resolves.toMatchObject({ paused: false });
    expect(storage.snapshot()?.paused).toBe(false);
    await expect(service.evaluateUrl("https://example.test/docs")).resolves.toEqual({ status: "allowed" });
  });

  it("survives browser restart by reading persisted controls", async () => {
    const storage = new InMemoryCaptureControlStorage();
    const firstRuntime = createService({ storage });
    await firstRuntime.pause();
    await firstRuntime.excludeDomain("docs.example.test");

    const restartedRuntime = createService({ storage, now: () => new Date("2026-07-12T12:05:00.000Z") });

    await expect(restartedRuntime.readState()).resolves.toEqual({
      schemaVersion: CAPTURE_CONTROL_SCHEMA_VERSION,
      paused: true,
      updatedAt: UPDATED_AT.toISOString(),
      excludedDomains: [
        {
          domain: "docs.example.test",
          createdAt: UPDATED_AT.toISOString()
        }
      ]
    });
  });

  it("excludes domains and matching subdomains", async () => {
    const service = createService();
    await service.excludeDomain("example.test");

    await expect(service.evaluateUrl("https://example.test/docs")).resolves.toEqual({
      status: "blocked",
      reason: "domain_excluded",
      domain: "example.test"
    });
    await expect(service.evaluateUrl("https://docs.example.test/reference")).resolves.toEqual({
      status: "blocked",
      reason: "domain_excluded",
      domain: "example.test"
    });
    await expect(service.evaluateUrl("https://notexample.test/docs")).resolves.toEqual({ status: "allowed" });
  });

  it("supports current-domain exclusion from a URL", async () => {
    const service = createService();

    const state = await service.excludeCurrentDomain("https://Docs.Example.Test/reference?token=secret");

    expect(state.excludedDomains).toEqual([
      {
        domain: "docs.example.test",
        createdAt: UPDATED_AT.toISOString()
      }
    ]);
  });

  it("supports undoing the most recent exclusion", async () => {
    const service = createService();
    await service.excludeDomain("first.example.test");
    await service.excludeDomain("second.example.test");

    const state = await service.undoLastDomainExclusion();

    expect(state.excludedDomains.map((exclusion) => exclusion.domain)).toEqual(["first.example.test"]);
    await expect(service.evaluateUrl("https://second.example.test/docs")).resolves.toEqual({ status: "allowed" });
  });

  it("removes a specific domain exclusion", async () => {
    const service = createService();
    await service.excludeDomain("first.example.test");
    await service.excludeDomain("second.example.test");

    const state = await service.removeDomainExclusion("https://first.example.test/path");

    expect(state.excludedDomains.map((exclusion) => exclusion.domain)).toEqual(["second.example.test"]);
  });

  it("updates runtime state immediately and notifies listeners", async () => {
    const service = createService();
    const observedStates: StoredCaptureControls[] = [];
    const listener: CaptureControlListener = (state) => observedStates.push(state);

    service.subscribe(listener);
    await service.pause();
    await service.resume();

    expect(observedStates.map((state) => state.paused)).toEqual([true, false]);
    expect(service.current().paused).toBe(false);
  });

  it("treats unsafe stored shapes as absent for migration safety", () => {
    expect(parseStoredCaptureControls({ schemaVersion: 999, paused: true })).toBeNull();
    expect(parseStoredCaptureControls({ schemaVersion: 1, paused: true, updatedAt: "bad-date", excludedDomains: [] })).toBeNull();
    expect(
      parseStoredCaptureControls({
        schemaVersion: 1,
        paused: false,
        updatedAt: UPDATED_AT.toISOString(),
        excludedDomains: [{ domain: "Example.test", createdAt: UPDATED_AT.toISOString() }]
      })
    ).toBeNull();
  });

  it("creates immutable persisted control state", async () => {
    const service = createService();
    const state = await service.excludeDomain("example.test");

    expect(Object.isFrozen(state)).toBe(true);
    expect(Object.isFrozen(state.excludedDomains)).toBe(true);
    expect(Object.isFrozen(state.excludedDomains[0])).toBe(true);
  });

  it("keeps capture allowed only when control decision allows it", () => {
    expect(isCaptureAllowedByControls({ status: "allowed" })).toBe(true);
    expect(isCaptureAllowedByControls({ status: "blocked", reason: "paused" })).toBe(false);
  });
});

function createService(options: Partial<CreateServiceOptions> = {}): CaptureControlRuntimeService {
  return new CaptureControlRuntimeService({
    now: options.now ?? (() => UPDATED_AT),
    storage: options.storage ?? new InMemoryCaptureControlStorage(options.initialState ?? null)
  });
}

interface CreateServiceOptions {
  readonly initialState: StoredCaptureControls | null;
  readonly now: () => Date;
  readonly storage: CaptureControlStorage;
}

class InMemoryCaptureControlStorage implements CaptureControlStorage {
  private storedState: StoredCaptureControls | null;

  constructor(initialState: StoredCaptureControls | null = null) {
    this.storedState = initialState;
  }

  read(): Promise<StoredCaptureControls | null> {
    return Promise.resolve(this.storedState);
  }

  write(state: StoredCaptureControls): Promise<void> {
    this.storedState = createStoredCaptureControls(state.paused, state.excludedDomains, new Date(state.updatedAt));
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.storedState = null;
    return Promise.resolve();
  }

  snapshot(): StoredCaptureControls | null {
    return this.storedState;
  }
}
