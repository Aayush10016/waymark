import type {
  FinalSolutionDraft,
  FinalSolutionDraftRepository,
  FinalSolutionDraftWriteResult
} from "../../storage/drafts";

export interface FinalSolutionDraftService {
  acknowledgeDeleted(problemSessionId: string): Promise<void>;
  read(problemSessionId: string): Promise<FinalSolutionDraft | null>;
  save(problemSessionId: string, text: string): Promise<FinalSolutionDraftWriteResult>;
}

/**
 * Application-facing service for user-authored final-solution draft durability.
 */
export class LocalFinalSolutionDraftService implements FinalSolutionDraftService {
  constructor(private readonly repository: FinalSolutionDraftRepository) {}

  acknowledgeDeleted(problemSessionId: string): Promise<void> {
    return this.repository.acknowledgeDeleted(problemSessionId);
  }

  read(problemSessionId: string): Promise<FinalSolutionDraft | null> {
    return this.repository.read(problemSessionId);
  }

  save(problemSessionId: string, text: string): Promise<FinalSolutionDraftWriteResult> {
    return this.repository.save({ problemSessionId, text });
  }
}
