export type CaptureEventSource = "tab_updated" | "web_navigation_committed" | "web_navigation_history_state";

export type CaptureResourceType = "ai_chat" | "documentation" | "forum" | "other" | "repository" | "search" | "video";

export interface CaptureEvent {
  readonly browserEventTime: number | null;
  readonly canonicalUrl: string;
  readonly capturedAt: string;
  readonly hostname: string;
  readonly navigationType: string | null;
  readonly openerTabId: number | null;
  readonly policyVersion: string;
  readonly problemSessionId: string | null;
  readonly removedQueryParameters: readonly string[];
  readonly resourceType: CaptureResourceType;
  readonly searchEngine: string | null;
  readonly searchQuery: string | null;
  readonly source: CaptureEventSource;
  readonly tabId: number;
  readonly title: string | null;
  readonly transitionQualifiers: readonly string[];
  readonly windowId: number | null;
}

export interface CaptureEventSink {
  emit(event: CaptureEvent): Promise<void>;
}
