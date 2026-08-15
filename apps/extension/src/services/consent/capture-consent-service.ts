import {
  createStoredCaptureConsent,
  CURRENT_CAPTURE_CONSENT_VERSION,
  type CaptureConsentStorage,
  type StoredCaptureConsent
} from "../../storage/consent/capture-consent-storage";

export type CaptureConsentStatus = "accepted" | "declined" | "missing" | "stale";

export type CaptureConsentState =
  | { readonly status: "accepted"; readonly accepted: true; readonly consentVersion: string; readonly decidedAt: string }
  | { readonly status: "declined"; readonly accepted: false; readonly consentVersion: string; readonly decidedAt: string }
  | { readonly status: "missing"; readonly accepted: false; readonly consentVersion: string | null; readonly decidedAt: null }
  | {
      readonly status: "stale";
      readonly accepted: false;
      readonly consentVersion: string;
      readonly requiredConsentVersion: string;
      readonly decidedAt: string;
    };

export type CaptureConsentListener = (state: CaptureConsentState) => void;

export interface CaptureConsentRuntimeServiceOptions {
  readonly storage: CaptureConsentStorage;
  readonly now?: () => Date;
  readonly requiredConsentVersion?: string;
}

/**
 * Owns runtime capture-consent state and persistence for the extension.
 */
export class CaptureConsentRuntimeService {
  private readonly listeners = new Set<CaptureConsentListener>();
  private readonly now: () => Date;
  private readonly requiredConsentVersion: string;
  private readonly storage: CaptureConsentStorage;
  private currentState: CaptureConsentState = missingConsentState();

  constructor(options: CaptureConsentRuntimeServiceOptions) {
    this.storage = options.storage;
    this.now = options.now ?? (() => new Date());
    this.requiredConsentVersion = options.requiredConsentVersion ?? CURRENT_CAPTURE_CONSENT_VERSION;
  }

  current(): CaptureConsentState {
    return this.currentState;
  }

  async readState(): Promise<CaptureConsentState> {
    const storedConsent = await this.storage.read();
    const nextState = toConsentState(storedConsent, this.requiredConsentVersion);
    this.setCurrentState(nextState);
    return nextState;
  }

  async accept(): Promise<CaptureConsentState> {
    return this.persistDecision(true);
  }

  async decline(): Promise<CaptureConsentState> {
    return this.persistDecision(false);
  }

  async revoke(): Promise<CaptureConsentState> {
    return this.persistDecision(false);
  }

  subscribe(listener: CaptureConsentListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private async persistDecision(accepted: boolean): Promise<CaptureConsentState> {
    const storedConsent = createStoredCaptureConsent(accepted, this.now());
    await this.storage.write(storedConsent);
    const nextState = toConsentState(storedConsent, this.requiredConsentVersion);
    this.setCurrentState(nextState);
    return nextState;
  }

  private setCurrentState(state: CaptureConsentState): void {
    this.currentState = state;
    for (const listener of this.listeners) {
      listener(state);
    }
  }
}

export function canCaptureWithConsent(state: CaptureConsentState): boolean {
  return state.status === "accepted";
}

function toConsentState(
  storedConsent: StoredCaptureConsent | null,
  requiredConsentVersion: string
): CaptureConsentState {
  if (storedConsent === null) {
    return missingConsentState();
  }

  if (storedConsent.consentVersion !== requiredConsentVersion) {
    return {
      status: "stale",
      accepted: false,
      consentVersion: storedConsent.consentVersion,
      requiredConsentVersion,
      decidedAt: storedConsent.decidedAt
    };
  }

  if (!storedConsent.accepted) {
    return {
      status: "declined",
      accepted: false,
      consentVersion: storedConsent.consentVersion,
      decidedAt: storedConsent.decidedAt
    };
  }

  return {
    status: "accepted",
    accepted: true,
    consentVersion: storedConsent.consentVersion,
    decidedAt: storedConsent.decidedAt
  };
}

function missingConsentState(): CaptureConsentState {
  return {
    status: "missing",
    accepted: false,
    consentVersion: null,
    decidedAt: null
  };
}
