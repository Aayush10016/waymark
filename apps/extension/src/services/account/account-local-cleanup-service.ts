import type { AuthSessionStorage } from "../../storage/auth/auth-session-storage";
import type { CaptureControlStorage } from "../../storage/capture-controls";
import type { CaptureEventQueueRepository } from "../../storage/capture-queue";
import type { CaptureConsentStorage } from "../../storage/consent/capture-consent-storage";
import type { FinalSolutionDraftRepository } from "../../storage/drafts";
import type { ProblemSessionRuntimeStorage } from "../../storage/problem-session";
import type { RecallRouteStorage } from "../../storage/recall";
import type { SidePanelRouteIntentStorage } from "../../storage/sidepanel";

export interface AccountLocalCleanupServiceOptions {
  readonly authStorage: AuthSessionStorage;
  readonly captureControlStorage: CaptureControlStorage;
  readonly captureQueue: CaptureEventQueueRepository;
  readonly consentStorage: CaptureConsentStorage;
  readonly draftRepository: FinalSolutionDraftRepository;
  readonly problemSessionStorage: ProblemSessionRuntimeStorage;
  readonly recallRouteStorage: RecallRouteStorage;
  readonly sidePanelRouteIntentStorage: SidePanelRouteIntentStorage;
}

export interface AccountLocalCleanupResult {
  readonly cleared: readonly string[];
}

/**
 * Clears device-local account data after confirmed account deletion.
 */
export class AccountLocalCleanupService {
  private readonly authStorage: AuthSessionStorage;
  private readonly captureControlStorage: CaptureControlStorage;
  private readonly captureQueue: CaptureEventQueueRepository;
  private readonly consentStorage: CaptureConsentStorage;
  private readonly draftRepository: FinalSolutionDraftRepository;
  private readonly problemSessionStorage: ProblemSessionRuntimeStorage;
  private readonly recallRouteStorage: RecallRouteStorage;
  private readonly sidePanelRouteIntentStorage: SidePanelRouteIntentStorage;

  constructor(options: AccountLocalCleanupServiceOptions) {
    this.authStorage = options.authStorage;
    this.captureControlStorage = options.captureControlStorage;
    this.captureQueue = options.captureQueue;
    this.consentStorage = options.consentStorage;
    this.draftRepository = options.draftRepository;
    this.problemSessionStorage = options.problemSessionStorage;
    this.recallRouteStorage = options.recallRouteStorage;
    this.sidePanelRouteIntentStorage = options.sidePanelRouteIntentStorage;
  }

  async clearLocalAccountData(): Promise<AccountLocalCleanupResult> {
    await this.authStorage.clear();
    await this.consentStorage.clear();
    await this.captureControlStorage.clear();
    await this.problemSessionStorage.clear();
    await this.recallRouteStorage.clear();
    await this.sidePanelRouteIntentStorage.clear();
    await this.captureQueue.clear();
    await this.draftRepository.clear();

    return Object.freeze({
      cleared: Object.freeze([
        "auth",
        "consent",
        "capture-controls",
        "problem-session-runtime",
        "recall-route",
        "side-panel-route-intent",
        "capture-queue",
        "final-solution-drafts"
      ])
    });
  }
}
