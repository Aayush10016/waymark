import type { CaptureConsentState } from "../consent";
import { canCaptureWithConsent } from "../consent";
import type { CaptureControlReader } from "../capture-controls";
import type { CapturePolicyRejectionReason, UrlQuerySanitizer } from "../capture-policy";
import type { CaptureEvent, CaptureEventSink, CaptureEventSource, CaptureResourceType } from "./capture-event";

const DEFAULT_DUPLICATE_WINDOW_MS = 5_000;
const UNKNOWN_CHROME_TAB_ID = -1;

export type { CaptureControlReader } from "../capture-controls";

export interface CaptureConsentReader {
  readState(): Promise<CaptureConsentState>;
}

export interface NavigationCaptureCandidate {
  readonly browserEventTime?: number;
  readonly frameId: number;
  readonly navigationType?: string | null;
  readonly openerTabId?: number | null;
  readonly source: CaptureEventSource;
  readonly tabId: number;
  readonly title?: string | null;
  readonly transitionQualifiers?: readonly string[];
  readonly url: string;
  readonly windowId?: number | null;
}

export type NavigationCaptureIgnoredReason =
  | "domain_excluded"
  | "duplicate"
  | "missing_consent"
  | "paused"
  | "sanitation_rejected"
  | "subframe"
  | "unsupported_tab";

export type NavigationCaptureResult =
  | { readonly status: "captured"; readonly event: CaptureEvent }
  | {
      readonly status: "ignored";
      readonly reason: Exclude<NavigationCaptureIgnoredReason, "sanitation_rejected">;
    }
  | {
      readonly status: "ignored";
      readonly reason: "sanitation_rejected";
      readonly rejectionReason: CapturePolicyRejectionReason;
    };

export interface NavigationCaptureServiceOptions {
  readonly consentReader: CaptureConsentReader;
  readonly controlReader: CaptureControlReader;
  readonly duplicateWindowMs?: number;
  readonly now?: () => Date;
  readonly sanitizer: UrlQuerySanitizer;
  readonly sink: CaptureEventSink;
}

/**
 * Applies Trail's consent, top-frame, sanitation, deduplication, and event-mapping rules for browser navigation capture.
 */
export class NavigationCaptureService {
  private readonly consentReader: CaptureConsentReader;
  private readonly controlReader: CaptureControlReader;
  private readonly duplicateWindowMs: number;
  private readonly now: () => Date;
  private readonly recentCaptures = new Map<string, number>();
  private readonly sanitizer: UrlQuerySanitizer;
  private readonly sink: CaptureEventSink;

  constructor(options: NavigationCaptureServiceOptions) {
    this.consentReader = options.consentReader;
    this.controlReader = options.controlReader;
    this.duplicateWindowMs = options.duplicateWindowMs ?? DEFAULT_DUPLICATE_WINDOW_MS;
    this.now = options.now ?? (() => new Date());
    this.sanitizer = options.sanitizer;
    this.sink = options.sink;
  }

  async capture(candidate: NavigationCaptureCandidate): Promise<NavigationCaptureResult> {
    if (candidate.frameId !== 0) {
      return { status: "ignored", reason: "subframe" };
    }

    if (candidate.tabId === UNKNOWN_CHROME_TAB_ID) {
      return { status: "ignored", reason: "unsupported_tab" };
    }

    const consentState = await this.consentReader.readState();
    if (!canCaptureWithConsent(consentState)) {
      return { status: "ignored", reason: "missing_consent" };
    }

    const controlDecision = await this.controlReader.evaluateUrl(candidate.url);
    if (controlDecision.status === "blocked") {
      return { status: "ignored", reason: controlDecision.reason };
    }

    const decision = this.sanitizer.sanitize({
      title: candidate.title ?? null,
      url: candidate.url
    });

    if (decision.decision === "reject") {
      return {
        status: "ignored",
        reason: "sanitation_rejected",
        rejectionReason: decision.rejectionReason
      };
    }

    const capturedAt = this.now();
    const duplicateKey = `${candidate.tabId}:${decision.canonicalUrl}`;
    if (this.isDuplicate(duplicateKey, capturedAt.getTime())) {
      return { status: "ignored", reason: "duplicate" };
    }

    this.rememberCapture(duplicateKey, capturedAt.getTime());

    const event = Object.freeze({
      browserEventTime: candidate.browserEventTime ?? null,
      canonicalUrl: decision.canonicalUrl,
      capturedAt: capturedAt.toISOString(),
      hostname: decision.hostname,
      navigationType: candidate.navigationType ?? null,
      openerTabId: candidate.openerTabId ?? null,
      policyVersion: decision.policyVersion,
      problemSessionId: null,
      removedQueryParameters: Object.freeze([...decision.removedQueryParameters]),
      resourceType: classifyResource(decision.hostname, decision.searchEngine, decision.canonicalUrl),
      searchEngine: decision.searchEngine,
      searchQuery: decision.searchQuery,
      source: candidate.source,
      tabId: candidate.tabId,
      title: decision.title,
      transitionQualifiers: Object.freeze([...(candidate.transitionQualifiers ?? [])]),
      windowId: candidate.windowId ?? null
    }) satisfies CaptureEvent;

    await this.sink.emit(event);

    return { status: "captured", event };
  }

  private isDuplicate(key: string, capturedAtEpochMs: number): boolean {
    this.pruneExpiredCaptures(capturedAtEpochMs);

    const previousCaptureEpochMs = this.recentCaptures.get(key);
    return previousCaptureEpochMs !== undefined && capturedAtEpochMs - previousCaptureEpochMs <= this.duplicateWindowMs;
  }

  private rememberCapture(key: string, capturedAtEpochMs: number): void {
    this.recentCaptures.set(key, capturedAtEpochMs);
  }

  private pruneExpiredCaptures(capturedAtEpochMs: number): void {
    for (const [key, previousCaptureEpochMs] of this.recentCaptures.entries()) {
      if (capturedAtEpochMs - previousCaptureEpochMs > this.duplicateWindowMs) {
        this.recentCaptures.delete(key);
      }
    }
  }
}

function classifyResource(hostname: string, searchEngine: string | null, canonicalUrl: string): CaptureResourceType {
  if (searchEngine !== null) {
    return "search";
  }

  if (hostname === "github.com" || hostname.endsWith(".github.com")) {
    return "repository";
  }

  if (
    hostname === "stackoverflow.com" ||
    hostname.endsWith(".stackoverflow.com") ||
    hostname === "reddit.com" ||
    hostname.endsWith(".reddit.com")
  ) {
    return "forum";
  }

  if (hostname === "youtube.com" || hostname === "www.youtube.com" || hostname === "youtu.be") {
    return "video";
  }

  if (hostname === "chat.openai.com" || hostname === "chatgpt.com" || hostname === "claude.ai") {
    return "ai_chat";
  }

  if (hostname.startsWith("docs.") || canonicalUrl.includes("/docs/") || canonicalUrl.includes("/documentation/")) {
    return "documentation";
  }

  return "other";
}
