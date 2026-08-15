import { describe, expect, it } from "vitest";
import { AccountLocalCleanupService } from "../src/services/account";
import type { AuthSessionStorage } from "../src/storage/auth/auth-session-storage";
import type { CaptureControlStorage } from "../src/storage/capture-controls";
import type { CaptureEventQueueRepository, CaptureQueueStatistics, StoredCaptureQueueRecord } from "../src/storage/capture-queue";
import type { CaptureConsentStorage } from "../src/storage/consent/capture-consent-storage";
import type { FinalSolutionDraft, FinalSolutionDraftRepository, FinalSolutionDraftWrite, FinalSolutionDraftWriteResult } from "../src/storage/drafts";
import type { ProblemSessionRuntimeStorage } from "../src/storage/problem-session";
import type { RecallRouteStorage } from "../src/storage/recall";
import type { SidePanelRouteIntent, SidePanelRouteIntentStorage } from "../src/storage/sidepanel";
import type { CaptureEvent } from "../src/services/navigation-capture";
import { defaultRecallState, type RecallStatePayload } from "@trail/extension-messages";

describe("AccountLocalCleanupService", () => {
  it("clears all account-owned local state including queues and drafts", async () => {
    const stores = createStores();
    const service = new AccountLocalCleanupService(stores);

    const result = await service.clearLocalAccountData();

    expect(result.cleared).toEqual([
      "auth",
      "consent",
      "capture-controls",
      "problem-session-runtime",
      "recall-route",
      "side-panel-route-intent",
      "capture-queue",
      "final-solution-drafts"
    ]);
    expect(stores.authStorage.cleared).toBe(true);
    expect(stores.consentStorage.cleared).toBe(true);
    expect(stores.captureControlStorage.cleared).toBe(true);
    expect(stores.problemSessionStorage.cleared).toBe(true);
    expect(stores.recallRouteStorage.cleared).toBe(true);
    expect(stores.sidePanelRouteIntentStorage.cleared).toBe(true);
    expect(stores.captureQueue.cleared).toBe(true);
    expect(stores.draftRepository.cleared).toBe(true);
  });
});

function createStores(): AccountLocalCleanupServiceConstructorFixture {
  return {
    authStorage: new ClearOnlyAuthStorage(),
    captureControlStorage: new ClearOnlyCaptureControlStorage(),
    captureQueue: new ClearOnlyCaptureQueue(),
    consentStorage: new ClearOnlyConsentStorage(),
    draftRepository: new ClearOnlyDraftRepository(),
    problemSessionStorage: new ClearOnlyProblemSessionStorage(),
    recallRouteStorage: new ClearOnlyRecallRouteStorage(),
    sidePanelRouteIntentStorage: new ClearOnlySidePanelRouteIntentStorage()
  };
}

interface AccountLocalCleanupServiceConstructorFixture {
  readonly authStorage: ClearOnlyAuthStorage;
  readonly captureControlStorage: ClearOnlyCaptureControlStorage;
  readonly captureQueue: ClearOnlyCaptureQueue;
  readonly consentStorage: ClearOnlyConsentStorage;
  readonly draftRepository: ClearOnlyDraftRepository;
  readonly problemSessionStorage: ClearOnlyProblemSessionStorage;
  readonly recallRouteStorage: ClearOnlyRecallRouteStorage;
  readonly sidePanelRouteIntentStorage: ClearOnlySidePanelRouteIntentStorage;
}

class ClearOnlyAuthStorage implements AuthSessionStorage {
  cleared = false;

  read(): Promise<null> {
    return Promise.resolve(null);
  }

  write(): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }
}

class ClearOnlyConsentStorage implements CaptureConsentStorage {
  cleared = false;

  read(): Promise<null> {
    return Promise.resolve(null);
  }

  write(): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }
}

class ClearOnlyCaptureControlStorage implements CaptureControlStorage {
  cleared = false;

  read(): Promise<null> {
    return Promise.resolve(null);
  }

  write(): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }
}

class ClearOnlyProblemSessionStorage implements ProblemSessionRuntimeStorage {
  cleared = false;

  read(): Promise<null> {
    return Promise.resolve(null);
  }

  write(): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }
}

class ClearOnlyRecallRouteStorage implements RecallRouteStorage {
  cleared = false;

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }

  read(): Promise<RecallStatePayload> {
    return Promise.resolve(defaultRecallState());
  }

  write(): Promise<void> {
    return Promise.resolve();
  }
}

class ClearOnlySidePanelRouteIntentStorage implements SidePanelRouteIntentStorage {
  cleared = false;

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }

  read(): Promise<SidePanelRouteIntent | null> {
    return Promise.resolve(null);
  }

  write(): Promise<void> {
    return Promise.resolve();
  }
}

class ClearOnlyCaptureQueue implements CaptureEventQueueRepository {
  cleared = false;

  append(event: CaptureEvent): Promise<StoredCaptureQueueRecord> {
    return Promise.resolve(event as StoredCaptureQueueRecord);
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }

  write(event: CaptureEvent): Promise<{ readonly record: StoredCaptureQueueRecord; readonly evictedRecords: readonly StoredCaptureQueueRecord[] }> {
    return Promise.resolve({ evictedRecords: [], record: event as StoredCaptureQueueRecord });
  }

  read(): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.resolve([]);
  }

  selectBatch(): Promise<readonly StoredCaptureQueueRecord[]> {
    return Promise.resolve([]);
  }

  remove(): Promise<void> {
    return Promise.resolve();
  }

  size(): Promise<number> {
    return Promise.resolve(0);
  }

  statistics(): Promise<CaptureQueueStatistics> {
    return Promise.resolve({
      maxBatchSize: 50,
      maxRecords: 1000,
      newestEnqueuedAt: null,
      oldestEnqueuedAt: null,
      remainingCapacity: 1000,
      totalRecords: 0
    });
  }
}

class ClearOnlyDraftRepository implements FinalSolutionDraftRepository {
  cleared = false;

  acknowledgeDeleted(): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.cleared = true;
    return Promise.resolve();
  }

  delete(): Promise<void> {
    return Promise.resolve();
  }

  read(): Promise<FinalSolutionDraft | null> {
    return Promise.resolve(null);
  }

  save(write: FinalSolutionDraftWrite): Promise<FinalSolutionDraftWriteResult> {
    const draft = {
      acknowledgedAt: null,
      createdAt: "2026-07-21T08:00:00.000Z",
      problemSessionId: write.problemSessionId,
      schemaVersion: 1,
      text: write.text,
      updatedAt: "2026-07-21T08:00:00.000Z"
    } as FinalSolutionDraft;
    return Promise.resolve({ draft, evictedDrafts: [] });
  }

  size(): Promise<number> {
    return Promise.resolve(0);
  }
}
