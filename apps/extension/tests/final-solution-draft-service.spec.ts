import { describe, expect, it } from "vitest";
import { LocalFinalSolutionDraftService } from "../src/services/drafts";
import type {
  FinalSolutionDraft,
  FinalSolutionDraftRepository,
  FinalSolutionDraftWrite,
  FinalSolutionDraftWriteResult
} from "../src/storage/drafts";

const SESSION_ID = "10000000-0000-4000-8000-000000000001";

describe("LocalFinalSolutionDraftService", () => {
  it("persists exact user-authored draft text through the repository", async () => {
    const repository = new InMemoryFinalSolutionDraftRepository();
    const service = new LocalFinalSolutionDraftService(repository);

    await service.save(SESSION_ID, "Allowed localhost in CorsConfiguration.");

    expect(repository.writes()).toEqual([
      {
        problemSessionId: SESSION_ID,
        text: "Allowed localhost in CorsConfiguration."
      }
    ]);
    await expect(service.read(SESSION_ID)).resolves.toMatchObject({
      problemSessionId: SESSION_ID,
      text: "Allowed localhost in CorsConfiguration."
    });
  });

  it("deletes acknowledged drafts through the repository", async () => {
    const repository = new InMemoryFinalSolutionDraftRepository();
    const service = new LocalFinalSolutionDraftService(repository);

    await service.save(SESSION_ID, "Solved with a JWT filter order change.");
    await service.acknowledgeDeleted(SESSION_ID);

    await expect(service.read(SESSION_ID)).resolves.toBeNull();
    expect(repository.acknowledgedIds()).toEqual([SESSION_ID]);
  });
});

class InMemoryFinalSolutionDraftRepository implements FinalSolutionDraftRepository {
  private readonly acknowledged: string[] = [];
  private readonly stored = new Map<string, FinalSolutionDraft>();
  private readonly writeLog: FinalSolutionDraftWrite[] = [];

  acknowledgeDeleted(problemSessionId: string): Promise<void> {
    this.acknowledged.push(problemSessionId);
    this.stored.delete(problemSessionId);
    return Promise.resolve();
  }

  delete(problemSessionId: string): Promise<void> {
    this.stored.delete(problemSessionId);
    return Promise.resolve();
  }

  clear(): Promise<void> {
    this.stored.clear();
    return Promise.resolve();
  }

  read(problemSessionId: string): Promise<FinalSolutionDraft | null> {
    return Promise.resolve(this.stored.get(problemSessionId) ?? null);
  }

  save(write: FinalSolutionDraftWrite): Promise<FinalSolutionDraftWriteResult> {
    this.writeLog.push(write);
    const draft: FinalSolutionDraft = {
      acknowledgedAt: null,
      createdAt: "2026-07-16T08:00:00.000Z",
      problemSessionId: write.problemSessionId,
      schemaVersion: 1,
      text: write.text,
      updatedAt: "2026-07-16T08:00:00.000Z"
    };
    this.stored.set(write.problemSessionId, draft);
    return Promise.resolve({ draft, evictedDrafts: [] });
  }

  size(): Promise<number> {
    return Promise.resolve(this.stored.size);
  }

  acknowledgedIds(): readonly string[] {
    return this.acknowledged;
  }

  writes(): readonly FinalSolutionDraftWrite[] {
    return this.writeLog;
  }
}
