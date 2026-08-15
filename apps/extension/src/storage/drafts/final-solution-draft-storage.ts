export const FINAL_SOLUTION_DRAFT_DATABASE_NAME = "trail-final-solution-drafts";
export const FINAL_SOLUTION_DRAFT_DATABASE_VERSION = 1;
export const FINAL_SOLUTION_DRAFT_STORE_NAME = "finalSolutionDrafts";
export const FINAL_SOLUTION_DRAFT_UPDATED_AT_INDEX = "byUpdatedAt";
export const FINAL_SOLUTION_DRAFT_SCHEMA_VERSION = 1;
export const DEFAULT_FINAL_SOLUTION_DRAFT_MAX_RECORDS = 25;
export const FINAL_SOLUTION_DRAFT_MAX_TEXT_LENGTH = 4_000;

export interface FinalSolutionDraft {
  readonly acknowledgedAt: string | null;
  readonly createdAt: string;
  readonly problemSessionId: string;
  readonly schemaVersion: typeof FINAL_SOLUTION_DRAFT_SCHEMA_VERSION;
  readonly text: string;
  readonly updatedAt: string;
}

export interface FinalSolutionDraftWrite {
  readonly problemSessionId: string;
  readonly text: string;
}

export interface FinalSolutionDraftWriteResult {
  readonly draft: FinalSolutionDraft;
  readonly evictedDrafts: readonly FinalSolutionDraft[];
}

export interface FinalSolutionDraftRepository {
  acknowledgeDeleted(problemSessionId: string): Promise<void>;
  clear(): Promise<void>;
  delete(problemSessionId: string): Promise<void>;
  read(problemSessionId: string): Promise<FinalSolutionDraft | null>;
  save(write: FinalSolutionDraftWrite): Promise<FinalSolutionDraftWriteResult>;
  size(): Promise<number>;
}

export interface IndexedDbFinalSolutionDraftRepositoryOptions {
  readonly databaseName?: string;
  readonly idbFactory?: IDBFactory;
  readonly maxRecords?: number;
  readonly now?: () => Date;
}

/**
 * Durable local IndexedDB repository for user-authored final-solution drafts.
 */
export class IndexedDbFinalSolutionDraftRepository implements FinalSolutionDraftRepository {
  private readonly databaseName: string;
  private readonly idbFactory: IDBFactory;
  private readonly maxRecords: number;
  private readonly now: () => Date;

  constructor(options: IndexedDbFinalSolutionDraftRepositoryOptions = {}) {
    this.databaseName = options.databaseName ?? FINAL_SOLUTION_DRAFT_DATABASE_NAME;
    this.idbFactory = options.idbFactory ?? indexedDB;
    this.maxRecords = validatePositiveInteger(options.maxRecords ?? DEFAULT_FINAL_SOLUTION_DRAFT_MAX_RECORDS, "maxRecords");
    this.now = options.now ?? (() => new Date());
  }

  async save(write: FinalSolutionDraftWrite): Promise<FinalSolutionDraftWriteResult> {
    const problemSessionId = validateProblemSessionId(write.problemSessionId);
    const text = validateDraftText(write.text);
    const updatedAt = this.now().toISOString();

    return this.withStore("readwrite", async (store) => {
      const existing: unknown = await requestToPromise<unknown>(store.get(problemSessionId));
      const draft = freezeFinalSolutionDraft({
        acknowledgedAt: null,
        createdAt: isFinalSolutionDraft(existing) ? existing.createdAt : updatedAt,
        problemSessionId,
        schemaVersion: FINAL_SOLUTION_DRAFT_SCHEMA_VERSION,
        text,
        updatedAt
      });

      await requestToPromise(store.put(toPersistableDraft(draft)));
      const evictedDrafts = await pruneOldestDraftsAboveCapacity(store, this.maxRecords, problemSessionId);

      return {
        draft,
        evictedDrafts
      };
    });
  }

  async read(problemSessionId: string): Promise<FinalSolutionDraft | null> {
    const validProblemSessionId = validateProblemSessionId(problemSessionId);
    return this.withStore("readonly", async (store) => {
      const value: unknown = await requestToPromise<unknown>(store.get(validProblemSessionId));
      return isFinalSolutionDraft(value) ? freezeFinalSolutionDraft(value) : null;
    });
  }

  async clear(): Promise<void> {
    await this.withStore("readwrite", async (store) => {
      await requestToPromise(store.clear());
    });
  }

  async delete(problemSessionId: string): Promise<void> {
    const validProblemSessionId = validateProblemSessionId(problemSessionId);
    await this.withStore("readwrite", async (store) => {
      await requestToPromise(store.delete(validProblemSessionId));
    });
  }

  async acknowledgeDeleted(problemSessionId: string): Promise<void> {
    const validProblemSessionId = validateProblemSessionId(problemSessionId);
    await this.withStore("readwrite", async (store) => {
      const existing: unknown = await requestToPromise<unknown>(store.get(validProblemSessionId));
      if (!isFinalSolutionDraft(existing)) {
        return;
      }

      const acknowledgedAt = this.now().toISOString();
      const acknowledgedDraft = freezeFinalSolutionDraft({
        ...existing,
        acknowledgedAt,
        updatedAt: acknowledgedAt
      });
      await requestToPromise(store.put(toPersistableDraft(acknowledgedDraft)));
      await requestToPromise(store.delete(validProblemSessionId));
    });
  }

  async size(): Promise<number> {
    return this.withStore("readonly", (store) => requestToPromise(store.count()));
  }

  private async withStore<T>(
    mode: IDBTransactionMode,
    operation: (store: IDBObjectStore) => Promise<T>
  ): Promise<T> {
    const database = await openFinalSolutionDraftDatabase(this.idbFactory, this.databaseName);

    try {
      const transaction = database.transaction(FINAL_SOLUTION_DRAFT_STORE_NAME, mode);
      const store = transaction.objectStore(FINAL_SOLUTION_DRAFT_STORE_NAME);
      const done = transactionDone(transaction);
      const result = await operation(store);
      await done;
      return result;
    } finally {
      database.close();
    }
  }
}

export function freezeFinalSolutionDraft(draft: FinalSolutionDraft): FinalSolutionDraft {
  return Object.freeze({ ...draft });
}

function openFinalSolutionDraftDatabase(idbFactory: IDBFactory, databaseName: string): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = idbFactory.open(databaseName, FINAL_SOLUTION_DRAFT_DATABASE_VERSION);

    request.onupgradeneeded = () => {
      const database = request.result;

      if (!database.objectStoreNames.contains(FINAL_SOLUTION_DRAFT_STORE_NAME)) {
        const store = database.createObjectStore(FINAL_SOLUTION_DRAFT_STORE_NAME, { keyPath: "problemSessionId" });
        store.createIndex(FINAL_SOLUTION_DRAFT_UPDATED_AT_INDEX, ["updatedAt", "problemSessionId"], { unique: false });
        return;
      }

      const store = request.transaction?.objectStore(FINAL_SOLUTION_DRAFT_STORE_NAME);
      if (store !== undefined && !store.indexNames.contains(FINAL_SOLUTION_DRAFT_UPDATED_AT_INDEX)) {
        store.createIndex(FINAL_SOLUTION_DRAFT_UPDATED_AT_INDEX, ["updatedAt", "problemSessionId"], { unique: false });
      }
    };

    request.onerror = () => reject(request.error ?? new Error("Failed to open final-solution draft database."));
    request.onsuccess = () => resolve(request.result);
  });
}

async function pruneOldestDraftsAboveCapacity(
  store: IDBObjectStore,
  maxRecords: number,
  protectedProblemSessionId: string
): Promise<readonly FinalSolutionDraft[]> {
  const totalRecords = await requestToPromise(store.count());
  const overflowCount = totalRecords - maxRecords;
  if (overflowCount <= 0) {
    return [];
  }

  return deleteOldestDrafts(store, overflowCount, protectedProblemSessionId);
}

function deleteOldestDrafts(
  store: IDBObjectStore,
  count: number,
  protectedProblemSessionId: string
): Promise<readonly FinalSolutionDraft[]> {
  return new Promise((resolve, reject) => {
    const deletedDrafts: FinalSolutionDraft[] = [];
    const request = store.index(FINAL_SOLUTION_DRAFT_UPDATED_AT_INDEX).openCursor();

    request.onerror = () => reject(request.error ?? new Error("Failed to enforce final-solution draft capacity."));
    request.onsuccess = () => {
      const cursor = request.result;

      if (cursor === null || deletedDrafts.length >= count) {
        resolve(deletedDrafts);
        return;
      }

      const draft: unknown = cursor.value;
      if (!isFinalSolutionDraft(draft)) {
        cursor.continue();
        return;
      }

      if (draft.problemSessionId === protectedProblemSessionId) {
        cursor.continue();
        return;
      }

      deletedDrafts.push(freezeFinalSolutionDraft(draft));
      const deleteRequest = cursor.delete();
      deleteRequest.onerror = () => reject(deleteRequest.error ?? new Error("Failed to evict final-solution draft."));
      deleteRequest.onsuccess = () => cursor.continue();
    };
  });
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onerror = () => reject(request.error ?? new Error("IndexedDB request failed."));
    request.onsuccess = () => resolve(request.result);
  });
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error("IndexedDB transaction failed."));
    transaction.onabort = () => reject(transaction.error ?? new Error("IndexedDB transaction aborted."));
  });
}

function toPersistableDraft(draft: FinalSolutionDraft): FinalSolutionDraft {
  return { ...draft };
}

function isFinalSolutionDraft(value: unknown): value is FinalSolutionDraft {
  return (
    isRecord(value) &&
    value.acknowledgedAt !== undefined &&
    (value.acknowledgedAt === null || isRfc3339Instant(value.acknowledgedAt)) &&
    isRfc3339Instant(value.createdAt) &&
    isUuidLike(value.problemSessionId) &&
    value.schemaVersion === FINAL_SOLUTION_DRAFT_SCHEMA_VERSION &&
    typeof value.text === "string" &&
    value.text.length <= FINAL_SOLUTION_DRAFT_MAX_TEXT_LENGTH &&
    isRfc3339Instant(value.updatedAt) &&
    Object.keys(value).length === 6
  );
}

function validateProblemSessionId(value: string): string {
  if (!isUuidLike(value)) {
    throw new Error("problemSessionId must be a valid UUID.");
  }

  return value;
}

function validateDraftText(value: string): string {
  if (value.length > FINAL_SOLUTION_DRAFT_MAX_TEXT_LENGTH) {
    throw new Error("final-solution draft text exceeds the maximum length.");
  }

  return value;
}

function validatePositiveInteger(value: number, name: string): number {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }

  return value;
}

function isRfc3339Instant(value: unknown): value is string {
  return typeof value === "string" && !Number.isNaN(Date.parse(value));
}

function isUuidLike(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
