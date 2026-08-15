import { IDBFactory } from "fake-indexeddb";
import { describe, expect, it } from "vitest";
import {
  FINAL_SOLUTION_DRAFT_DATABASE_VERSION,
  FINAL_SOLUTION_DRAFT_MAX_TEXT_LENGTH,
  FINAL_SOLUTION_DRAFT_SCHEMA_VERSION,
  FINAL_SOLUTION_DRAFT_STORE_NAME,
  IndexedDbFinalSolutionDraftRepository
} from "../src/storage/drafts";

const SESSION_ONE = "10000000-0000-4000-8000-000000000001";
const SESSION_TWO = "10000000-0000-4000-8000-000000000002";
const SESSION_THREE = "10000000-0000-4000-8000-000000000003";
const SESSION_FOUR = "10000000-0000-4000-8000-000000000004";

describe("IndexedDbFinalSolutionDraftRepository", () => {
  it("creates a versioned IndexedDB database for final-solution drafts", async () => {
    const idbFactory = new IDBFactory();
    const repository = createRepository({ idbFactory });

    await repository.save({ problemSessionId: SESSION_ONE, text: "Allowed localhost in CorsConfiguration." });

    const database = await openRawDatabase(idbFactory, "trail-final-solution-drafts-test", FINAL_SOLUTION_DRAFT_DATABASE_VERSION);
    expect(database.objectStoreNames.contains(FINAL_SOLUTION_DRAFT_STORE_NAME)).toBe(true);
    expect(database.version).toBe(FINAL_SOLUTION_DRAFT_DATABASE_VERSION);
    database.close();
  });

  it("autosaves a user-authored draft by Problem Session ID", async () => {
    const repository = createRepository();

    const result = await repository.save({
      problemSessionId: SESSION_ONE,
      text: "Allowed localhost:3000 in CorsConfiguration."
    });

    expect(result.evictedDrafts).toEqual([]);
    expect(result.draft).toEqual({
      acknowledgedAt: null,
      createdAt: "2026-07-16T08:00:00.000Z",
      problemSessionId: SESSION_ONE,
      schemaVersion: FINAL_SOLUTION_DRAFT_SCHEMA_VERSION,
      text: "Allowed localhost:3000 in CorsConfiguration.",
      updatedAt: "2026-07-16T08:00:00.000Z"
    });
    await expect(repository.read(SESSION_ONE)).resolves.toEqual(result.draft);
  });

  it("updates an existing draft without changing creation time", async () => {
    const repository = createRepository({
      now: sequentialDates(["2026-07-16T08:00:00.000Z", "2026-07-16T08:01:00.000Z"])
    });

    await repository.save({ problemSessionId: SESSION_ONE, text: "First solution text." });
    const updated = await repository.save({ problemSessionId: SESSION_ONE, text: "Updated solution text." });

    expect(updated.draft).toMatchObject({
      createdAt: "2026-07-16T08:00:00.000Z",
      text: "Updated solution text.",
      updatedAt: "2026-07-16T08:01:00.000Z"
    });
    await expect(repository.size()).resolves.toBe(1);
  });

  it("persists drafts across browser and service-worker restarts", async () => {
    const idbFactory = new IDBFactory();
    const firstWorkerRepository = createRepository({ idbFactory });

    await firstWorkerRepository.save({ problemSessionId: SESSION_ONE, text: "Persisted local draft." });

    const restartedWorkerRepository = createRepository({ idbFactory });
    await expect(restartedWorkerRepository.read(SESSION_ONE)).resolves.toMatchObject({
      problemSessionId: SESSION_ONE,
      text: "Persisted local draft."
    });
  });

  it("works offline because it never depends on network state", async () => {
    const repository = createRepository();

    await expect(
      repository.save({ problemSessionId: SESSION_ONE, text: "Offline draft remains local." })
    ).resolves.toMatchObject({
      draft: {
        problemSessionId: SESSION_ONE,
        text: "Offline draft remains local."
      }
    });
  });

  it("deletes a draft after backend acknowledgement", async () => {
    const repository = createRepository();

    await repository.save({ problemSessionId: SESSION_ONE, text: "Acknowledged draft." });
    await repository.acknowledgeDeleted(SESSION_ONE);

    await expect(repository.read(SESSION_ONE)).resolves.toBeNull();
    await expect(repository.size()).resolves.toBe(0);
  });

  it("treats acknowledged deletion of a missing draft as idempotent", async () => {
    const repository = createRepository();

    await expect(repository.acknowledgeDeleted(SESSION_ONE)).resolves.toBeUndefined();
    await expect(repository.size()).resolves.toBe(0);
  });

  it("supports explicit draft deletion for abandoned local flows", async () => {
    const repository = createRepository();

    await repository.save({ problemSessionId: SESSION_ONE, text: "Remove me." });
    await repository.delete(SESSION_ONE);

    await expect(repository.read(SESSION_ONE)).resolves.toBeNull();
  });

  it("enforces configured capacity by evicting oldest non-current drafts", async () => {
    const repository = createRepository({
      maxRecords: 2,
      now: sequentialDates([
        "2026-07-16T08:00:00.000Z",
        "2026-07-16T08:01:00.000Z",
        "2026-07-16T08:02:00.000Z"
      ])
    });

    await repository.save({ problemSessionId: SESSION_ONE, text: "Oldest draft." });
    await repository.save({ problemSessionId: SESSION_TWO, text: "Middle draft." });
    const result = await repository.save({ problemSessionId: SESSION_THREE, text: "Newest draft." });

    expect(result.evictedDrafts).toEqual([expect.objectContaining({ problemSessionId: SESSION_ONE })]);
    await expect(repository.read(SESSION_ONE)).resolves.toBeNull();
    await expect(repository.read(SESSION_TWO)).resolves.toMatchObject({ text: "Middle draft." });
    await expect(repository.read(SESSION_THREE)).resolves.toMatchObject({ text: "Newest draft." });
  });

  it("never evicts the draft currently being saved when enforcing capacity", async () => {
    const repository = createRepository({
      maxRecords: 1,
      now: sequentialDates([
        "2026-07-16T08:00:00.000Z",
        "2026-07-16T08:01:00.000Z",
        "2026-07-16T08:02:00.000Z"
      ])
    });

    await repository.save({ problemSessionId: SESSION_ONE, text: "First draft." });
    await repository.save({ problemSessionId: SESSION_TWO, text: "Second draft." });
    await repository.save({ problemSessionId: SESSION_TWO, text: "Second draft update." });

    await expect(repository.read(SESSION_TWO)).resolves.toMatchObject({ text: "Second draft update." });
    await expect(repository.size()).resolves.toBe(1);
  });

  it("isolates drafts by Problem Session ID", async () => {
    const repository = createRepository();

    await repository.save({ problemSessionId: SESSION_ONE, text: "One." });
    await repository.save({ problemSessionId: SESSION_TWO, text: "Two." });

    await expect(repository.read(SESSION_ONE)).resolves.toMatchObject({ text: "One." });
    await expect(repository.read(SESSION_TWO)).resolves.toMatchObject({ text: "Two." });
  });

  it("keeps stored drafts immutable", async () => {
    const repository = createRepository();

    await repository.save({ problemSessionId: SESSION_ONE, text: "Immutable draft." });
    const draft = await repository.read(SESSION_ONE);

    expect(Object.isFrozen(draft)).toBe(true);
  });

  it("rejects invalid Problem Session IDs", async () => {
    const repository = createRepository();

    await expect(repository.save({ problemSessionId: "not-a-session-id", text: "Draft." })).rejects.toThrow(
      "problemSessionId must be a valid UUID."
    );
  });

  it("allows empty local text while the user is editing but enforces a bounded maximum", async () => {
    const repository = createRepository();

    await expect(repository.save({ problemSessionId: SESSION_ONE, text: "" })).resolves.toMatchObject({
      draft: { text: "" }
    });
    await expect(
      repository.save({ problemSessionId: SESSION_TWO, text: "x".repeat(FINAL_SOLUTION_DRAFT_MAX_TEXT_LENGTH + 1) })
    ).rejects.toThrow("final-solution draft text exceeds the maximum length.");
  });

  it("uses deterministic ordering for capacity after multiple updates", async () => {
    const repository = createRepository({
      maxRecords: 3,
      now: sequentialDates([
        "2026-07-16T08:00:00.000Z",
        "2026-07-16T08:01:00.000Z",
        "2026-07-16T08:02:00.000Z",
        "2026-07-16T08:03:00.000Z",
        "2026-07-16T08:04:00.000Z"
      ])
    });

    await repository.save({ problemSessionId: SESSION_ONE, text: "Old first." });
    await repository.save({ problemSessionId: SESSION_TWO, text: "Second." });
    await repository.save({ problemSessionId: SESSION_THREE, text: "Third." });
    await repository.save({ problemSessionId: SESSION_ONE, text: "Updated first." });
    const result = await repository.save({ problemSessionId: SESSION_FOUR, text: "Fourth." });

    expect(result.evictedDrafts).toEqual([expect.objectContaining({ problemSessionId: SESSION_TWO })]);
    await expect(repository.read(SESSION_ONE)).resolves.toMatchObject({ text: "Updated first." });
    await expect(repository.read(SESSION_TWO)).resolves.toBeNull();
    await expect(repository.read(SESSION_THREE)).resolves.toMatchObject({ text: "Third." });
    await expect(repository.read(SESSION_FOUR)).resolves.toMatchObject({ text: "Fourth." });
  });
});

function createRepository(options: CreateRepositoryOptions = {}): IndexedDbFinalSolutionDraftRepository {
  return new IndexedDbFinalSolutionDraftRepository({
    databaseName: options.databaseName ?? "trail-final-solution-drafts-test",
    idbFactory: options.idbFactory ?? new IDBFactory(),
    ...(options.maxRecords === undefined ? {} : { maxRecords: options.maxRecords }),
    now: options.now ?? (() => new Date("2026-07-16T08:00:00.000Z"))
  });
}

function sequentialDates(values: readonly string[]): () => Date {
  let index = 0;
  return () => {
    const value = values[index];
    if (value === undefined) {
      throw new Error("Date fixture exhausted.");
    }

    index += 1;
    return new Date(value);
  };
}

function openRawDatabase(idbFactory: IDBFactory, databaseName: string, version: number): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = idbFactory.open(databaseName, version);
    request.onerror = () => reject(request.error ?? new Error("Failed to open raw database."));
    request.onsuccess = () => resolve(request.result);
  });
}

interface CreateRepositoryOptions {
  readonly databaseName?: string;
  readonly idbFactory?: IDBFactory;
  readonly maxRecords?: number;
  readonly now?: () => Date;
}
