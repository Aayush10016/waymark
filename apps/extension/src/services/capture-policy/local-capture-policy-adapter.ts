import type { AllowedCapturePolicyDecision, CapturePolicyDecision, RawCaptureCandidate, UrlQuerySanitizer } from "./url-query-sanitizer";

export interface SanitizedCaptureEvent {
  readonly canonicalUrl: string;
  readonly hostname: string;
  readonly policyVersion: string;
  readonly removedQueryParameters: readonly string[];
  readonly searchEngine: string | null;
  readonly searchQuery: string | null;
  readonly title: string | null;
}

export interface SanitizedCaptureEventQueue {
  enqueue(event: SanitizedCaptureEvent): Promise<void>;
}

export interface CapturePolicyAdapterResult {
  readonly decision: CapturePolicyDecision;
  readonly queued: boolean;
}

/**
 * Coordinates local sanitation with the capture queue boundary and refuses to enqueue rejected capture candidates.
 */
export class LocalCapturePolicyAdapter {
  private readonly sanitizer: UrlQuerySanitizer;
  private readonly queue: SanitizedCaptureEventQueue;

  constructor(sanitizer: UrlQuerySanitizer, queue: SanitizedCaptureEventQueue) {
    this.sanitizer = sanitizer;
    this.queue = queue;
  }

  async sanitizeAndEnqueue(input: RawCaptureCandidate): Promise<CapturePolicyAdapterResult> {
    const decision = this.sanitizer.sanitize(input);

    if (decision.decision === "reject") {
      return {
        decision,
        queued: false
      };
    }

    await this.queue.enqueue(toSanitizedCaptureEvent(decision));

    return {
      decision,
      queued: true
    };
  }
}

function toSanitizedCaptureEvent(decision: AllowedCapturePolicyDecision): SanitizedCaptureEvent {
  return {
    canonicalUrl: decision.canonicalUrl,
    hostname: decision.hostname,
    policyVersion: decision.policyVersion,
    removedQueryParameters: decision.removedQueryParameters,
    searchEngine: decision.searchEngine,
    searchQuery: decision.searchQuery,
    title: decision.title
  };
}
