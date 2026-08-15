import type { CaptureEvent, CaptureEventSink } from "../../services/navigation-capture";

export const CAPTURE_QUEUE_DATABASE_NAME = "trail-capture-queue";
export const CAPTURE_QUEUE_DATABASE_VERSION = 1;
export const CAPTURE_QUEUE_STORE_NAME = "captureEvents";
export const CAPTURE_QUEUE_ENQUEUED_AT_INDEX = "byEnqueuedAt";
export const CAPTURE_QUEUE_RECORD_SCHEMA_VERSION = 1;
export const DEFAULT_CAPTURE_QUEUE_MAX_RECORDS = 1_000;
export const DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE = 50;

export interface StoredCaptureQueueRecord extends CaptureEvent {
  readonly id: string;
  readonly schemaVersion: typeof CAPTURE_QUEUE_RECORD_SCHEMA_VERSION;
  readonly enqueuedAt: string;
}

export interface CaptureQueueReadOptions {
  readonly limit?: number;
}

export interface CaptureQueueBatchSelectionOptions {
  readonly maxBatchSize?: number;
}

export interface CaptureQueueWriteResult {
  readonly record: StoredCaptureQueueRecord;
  readonly evictedRecords: readonly StoredCaptureQueueRecord[];
}

export interface CaptureQueueStatistics {
  readonly maxBatchSize: number;
  readonly maxRecords: number;
  readonly oldestEnqueuedAt: string | null;
  readonly newestEnqueuedAt: string | null;
  readonly remainingCapacity: number;
  readonly totalRecords: number;
}

export interface CaptureEventQueueRepository {
  append(event: CaptureEvent): Promise<StoredCaptureQueueRecord>;
  clear(): Promise<void>;
  write(event: CaptureEvent): Promise<CaptureQueueWriteResult>;
  read(options?: CaptureQueueReadOptions): Promise<readonly StoredCaptureQueueRecord[]>;
  selectBatch(options?: CaptureQueueBatchSelectionOptions): Promise<readonly StoredCaptureQueueRecord[]>;
  remove(ids: readonly string[]): Promise<void>;
  size(): Promise<number>;
  statistics(): Promise<CaptureQueueStatistics>;
}

export interface IndexedDbCaptureEventQueueOptions {
  readonly databaseName?: string;
  readonly idbFactory?: IDBFactory;
  readonly idFactory?: () => string;
  readonly maxBatchSize?: number;
  readonly maxRecords?: number;
  readonly now?: () => Date;
}

/**
 * Durable local IndexedDB repository for sanitized capture events emitted by the browser capture pipeline.
 */
export class IndexedDbCaptureEventQueue implements CaptureEventQueueRepository, CaptureEventSink {
  private readonly databaseName: string;
  private readonly idbFactory: IDBFactory;
  private readonly idFactory: () => string;
  private readonly maxBatchSize: number;
  private readonly maxRecords: number;
  private readonly now: () => Date;

  constructor(options: IndexedDbCaptureEventQueueOptions = {}) {
    this.databaseName = options.databaseName ?? CAPTURE_QUEUE_DATABASE_NAME;
    this.idbFactory = options.idbFactory ?? indexedDB;
    this.idFactory = options.idFactory ?? createCaptureQueueRecordId;
    this.maxBatchSize = validatePositiveInteger(options.maxBatchSize ?? DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE, "maxBatchSize");
    this.maxRecords = validatePositiveInteger(options.maxRecords ?? DEFAULT_CAPTURE_QUEUE_MAX_RECORDS, "maxRecords");
    this.now = options.now ?? (() => new Date());
  }

  async emit(event: CaptureEvent): Promise<void> {
    await this.write(event);
  }

  async append(event: CaptureEvent): Promise<StoredCaptureQueueRecord> {
    return (await this.write(event)).record;
  }

  async clear(): Promise<void> {
    await this.withStore("readwrite", async (store) => {
      await requestToPromise(store.clear());
    });
  }

  async write(event: CaptureEvent): Promise<CaptureQueueWriteResult> {
    const record = freezeStoredCaptureQueueRecord({
      ...event,
      id: this.idFactory(),
      schemaVersion: CAPTURE_QUEUE_RECORD_SCHEMA_VERSION,
      enqueuedAt: this.now().toISOString(),
      removedQueryParameters: [...event.removedQueryParameters],
      transitionQualifiers: [...event.transitionQualifiers]
    });

    const evictedRecords = await this.withStore("readwrite", async (store) => {
      await requestToPromise(store.add(toPersistableRecord(record)));
      return pruneOldestRecordsAboveCapacity(store, this.maxRecords);
    });

    return {
      record,
      evictedRecords
    };
  }

  async read(options: CaptureQueueReadOptions = {}): Promise<readonly StoredCaptureQueueRecord[]> {
    return this.withStore("readonly", (store) => readOrderedRecords(store, options.limit));
  }

  async selectBatch(options: CaptureQueueBatchSelectionOptions = {}): Promise<readonly StoredCaptureQueueRecord[]> {
    const requestedBatchSize = options.maxBatchSize ?? this.maxBatchSize;
    const effectiveBatchSize = Math.min(validatePositiveInteger(requestedBatchSize, "maxBatchSize"), this.maxBatchSize);
    return this.read({ limit: effectiveBatchSize });
  }

  async remove(ids: readonly string[]): Promise<void> {
    if (ids.length === 0) {
      return;
    }

    await this.withStore("readwrite", async (store) => {
      await Promise.all(ids.map((id) => requestToPromise(store.delete(id))));
    });
  }

  async size(): Promise<number> {
    return this.withStore("readonly", (store) => requestToPromise(store.count()));
  }

  async statistics(): Promise<CaptureQueueStatistics> {
    return this.withStore("readonly", async (store) => {
      const totalRecords = await requestToPromise(store.count());
      const oldestRecord = await readBoundaryRecord(store, "next");
      const newestRecord = await readBoundaryRecord(store, "prev");

      return {
        maxBatchSize: this.maxBatchSize,
        maxRecords: this.maxRecords,
        oldestEnqueuedAt: oldestRecord?.enqueuedAt ?? null,
        newestEnqueuedAt: newestRecord?.enqueuedAt ?? null,
        remainingCapacity: Math.max(this.maxRecords - totalRecords, 0),
        totalRecords
      };
    });
  }

  private async withStore<T>(
    mode: IDBTransactionMode,
    operation: (store: IDBObjectStore) => Promise<T>
  ): Promise<T> {
    const database = await openCaptureQueueDatabase(this.idbFactory, this.databaseName);

    try {
      const transaction = database.transaction(CAPTURE_QUEUE_STORE_NAME, mode);
      const store = transaction.objectStore(CAPTURE_QUEUE_STORE_NAME);
      const done = transactionDone(transaction);
      const result = await operation(store);
      await done;
      return result;
    } finally {
      database.close();
    }
  }
}

export function freezeStoredCaptureQueueRecord(record: StoredCaptureQueueRecord): StoredCaptureQueueRecord {
  return Object.freeze({
    ...record,
    removedQueryParameters: Object.freeze([...record.removedQueryParameters]),
    transitionQualifiers: Object.freeze([...record.transitionQualifiers])
  });
}

function openCaptureQueueDatabase(idbFactory: IDBFactory, databaseName: string): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = idbFactory.open(databaseName, CAPTURE_QUEUE_DATABASE_VERSION);

    request.onupgradeneeded = () => {
      const database = request.result;

      if (!database.objectStoreNames.contains(CAPTURE_QUEUE_STORE_NAME)) {
        const store = database.createObjectStore(CAPTURE_QUEUE_STORE_NAME, { keyPath: "id" });
        store.createIndex(CAPTURE_QUEUE_ENQUEUED_AT_INDEX, ["enqueuedAt", "id"], { unique: false });
        return;
      }

      const store = request.transaction?.objectStore(CAPTURE_QUEUE_STORE_NAME);
      if (store !== undefined && !store.indexNames.contains(CAPTURE_QUEUE_ENQUEUED_AT_INDEX)) {
        store.createIndex(CAPTURE_QUEUE_ENQUEUED_AT_INDEX, ["enqueuedAt", "id"], { unique: false });
      }
    };

    request.onerror = () => reject(request.error ?? new Error("Failed to open capture queue database."));
    request.onsuccess = () => resolve(request.result);
  });
}

function readOrderedRecords(store: IDBObjectStore, limit: number | undefined): Promise<readonly StoredCaptureQueueRecord[]> {
  return new Promise((resolve, reject) => {
    const records: StoredCaptureQueueRecord[] = [];
    const request = store.index(CAPTURE_QUEUE_ENQUEUED_AT_INDEX).openCursor();

    request.onerror = () => reject(request.error ?? new Error("Failed to read capture queue."));
    request.onsuccess = () => {
      const cursor = request.result;

      if (cursor === null || (limit !== undefined && records.length >= limit)) {
        resolve(records);
        return;
      }

      records.push(freezeStoredCaptureQueueRecord(cursor.value as StoredCaptureQueueRecord));
      cursor.continue();
    };
  });
}

async function pruneOldestRecordsAboveCapacity(
  store: IDBObjectStore,
  maxRecords: number
): Promise<readonly StoredCaptureQueueRecord[]> {
  const totalRecords = await requestToPromise(store.count());
  const overflowCount = totalRecords - maxRecords;
  if (overflowCount <= 0) {
    return [];
  }

  return deleteOldestRecords(store, overflowCount);
}

function deleteOldestRecords(store: IDBObjectStore, count: number): Promise<readonly StoredCaptureQueueRecord[]> {
  return new Promise((resolve, reject) => {
    const deletedRecords: StoredCaptureQueueRecord[] = [];
    const request = store.index(CAPTURE_QUEUE_ENQUEUED_AT_INDEX).openCursor();

    request.onerror = () => reject(request.error ?? new Error("Failed to enforce capture queue capacity."));
    request.onsuccess = () => {
      const cursor = request.result;

      if (cursor === null || deletedRecords.length >= count) {
        resolve(deletedRecords);
        return;
      }

      deletedRecords.push(freezeStoredCaptureQueueRecord(cursor.value as StoredCaptureQueueRecord));
      const deleteRequest = cursor.delete();
      deleteRequest.onerror = () => reject(deleteRequest.error ?? new Error("Failed to evict capture queue record."));
      deleteRequest.onsuccess = () => cursor.continue();
    };
  });
}

function readBoundaryRecord(
  store: IDBObjectStore,
  direction: IDBCursorDirection
): Promise<StoredCaptureQueueRecord | null> {
  return new Promise((resolve, reject) => {
    const request = store.index(CAPTURE_QUEUE_ENQUEUED_AT_INDEX).openCursor(null, direction);

    request.onerror = () => reject(request.error ?? new Error("Failed to read capture queue boundary."));
    request.onsuccess = () => {
      const cursor = request.result;
      resolve(cursor === null ? null : freezeStoredCaptureQueueRecord(cursor.value as StoredCaptureQueueRecord));
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

function toPersistableRecord(record: StoredCaptureQueueRecord): StoredCaptureQueueRecord {
  return {
    ...record,
    removedQueryParameters: [...record.removedQueryParameters],
    transitionQualifiers: [...record.transitionQualifiers]
  };
}

function createCaptureQueueRecordId(): string {
  return crypto.randomUUID();
}

function validatePositiveInteger(value: number, name: string): number {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }

  return value;
}
