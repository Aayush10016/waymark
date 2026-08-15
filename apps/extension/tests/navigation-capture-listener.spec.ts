import { describe, expect, it } from "vitest";
import { installNavigationCaptureListenerAdapters } from "../src/background/navigation-capture-listener";
import type {
  NavigationCaptureCandidate,
  NavigationCaptureService,
  NavigationCaptureResult
} from "../src/services/navigation-capture";

describe("navigation capture listener adapters", () => {
  it("registers webNavigation and tabs listeners without eagerly capturing", () => {
    const api = new FakeChromeApi();
    const captureService = new RecordingNavigationCaptureService();

    installNavigationCaptureListenerAdapters({
      api: api.chromeApi(),
      captureService: captureService.asNavigationCaptureService(),
      tabMetadataProvider: {
        get: () =>
          Promise.resolve({
            openerTabId: null,
            title: "Docs title",
            url: "https://example.test/docs",
            windowId: 1
          })
      }
    });

    expect(api.webNavigationCommittedListeners()).toHaveLength(1);
    expect(api.webNavigationHistoryStateListeners()).toHaveLength(1);
    expect(api.tabsUpdatedListeners()).toHaveLength(1);
    expect(captureService.candidates()).toEqual([]);
  });

  it("passes only top-level webNavigation events to the capture service", async () => {
    const api = new FakeChromeApi();
    const captureService = new RecordingNavigationCaptureService();

    installNavigationCaptureListenerAdapters({
      api: api.chromeApi(),
      captureService: captureService.asNavigationCaptureService(),
      tabMetadataProvider: {
        get: () =>
          Promise.resolve({
            openerTabId: 11,
            title: "Resolved title",
            url: "https://example.test/docs",
            windowId: 2
          })
      }
    });

    api.emitCommitted({
      frameId: 1,
      tabId: 5,
      timeStamp: 100,
      transitionQualifiers: [],
      transitionType: "auto_subframe",
      url: "https://example.test/frame"
    });
    api.emitCommitted({
      frameId: 0,
      tabId: 5,
      timeStamp: 101,
      transitionQualifiers: ["server_redirect"],
      transitionType: "link",
      url: "https://example.test/docs"
    });

    await Promise.resolve();
    await Promise.resolve();

    expect(captureService.candidates()).toEqual([
      {
        browserEventTime: 101,
        frameId: 0,
        navigationType: "link",
        openerTabId: 11,
        source: "web_navigation_committed",
        tabId: 5,
        title: "Resolved title",
        transitionQualifiers: ["server_redirect"],
        url: "https://example.test/docs",
        windowId: 2
      }
    ]);
  });

  it("maps tab updates to top-level capture candidates", () => {
    const api = new FakeChromeApi();
    const captureService = new RecordingNavigationCaptureService();

    installNavigationCaptureListenerAdapters({
      api: api.chromeApi(),
      captureService: captureService.asNavigationCaptureService(),
      tabMetadataProvider: {
        get: () => Promise.resolve({ openerTabId: null, title: null, url: null, windowId: null })
      }
    });

    api.emitTabUpdated(9, { status: "loading" }, { id: 9, windowId: 4 });
    api.emitTabUpdated(
      9,
      { status: "complete" },
      {
        id: 9,
        openerTabId: 3,
        title: "Completed title",
        url: "https://example.test/complete",
        windowId: 4
      }
    );

    expect(captureService.candidates()).toEqual([
      {
        frameId: 0,
        openerTabId: 3,
        source: "tab_updated",
        tabId: 9,
        title: "Completed title",
        url: "https://example.test/complete",
        windowId: 4
      }
    ]);
  });
});

class RecordingNavigationCaptureService {
  private readonly capturedCandidates: NavigationCaptureCandidate[] = [];

  asNavigationCaptureService(): NavigationCaptureService {
    return {
      capture: (candidate: NavigationCaptureCandidate): Promise<NavigationCaptureResult> => {
        this.capturedCandidates.push(candidate);
        return Promise.resolve({ status: "ignored", reason: "duplicate" });
      }
    } as NavigationCaptureService;
  }

  candidates(): readonly NavigationCaptureCandidate[] {
    return this.capturedCandidates;
  }
}

class FakeChromeApi {
  private readonly committedListeners: Array<(details: WebNavigationDetailsFixture) => void> = [];
  private readonly historyStateListeners: Array<(details: WebNavigationDetailsFixture) => void> = [];
  private readonly tabUpdatedListeners: Array<(tabId: number, changeInfo: TabChangeInfoFixture, tab: TabFixture) => void> = [];

  chromeApi(): typeof chrome {
    return {
      runtime: {
        lastError: undefined
      },
      tabs: {
        get: () => undefined,
        onUpdated: {
          addListener: (listener: (tabId: number, changeInfo: TabChangeInfoFixture, tab: TabFixture) => void) => {
            this.tabUpdatedListeners.push(listener);
          }
        }
      },
      webNavigation: {
        onCommitted: {
          addListener: (listener: (details: WebNavigationDetailsFixture) => void) => {
            this.committedListeners.push(listener);
          }
        },
        onHistoryStateUpdated: {
          addListener: (listener: (details: WebNavigationDetailsFixture) => void) => {
            this.historyStateListeners.push(listener);
          }
        }
      }
    } as unknown as typeof chrome;
  }

  emitCommitted(details: WebNavigationDetailsFixture): void {
    for (const listener of this.committedListeners) {
      listener(details);
    }
  }

  emitTabUpdated(tabId: number, changeInfo: TabChangeInfoFixture, tab: TabFixture): void {
    for (const listener of this.tabUpdatedListeners) {
      listener(tabId, changeInfo, tab);
    }
  }

  tabsUpdatedListeners(): readonly unknown[] {
    return this.tabUpdatedListeners;
  }

  webNavigationCommittedListeners(): readonly unknown[] {
    return this.committedListeners;
  }

  webNavigationHistoryStateListeners(): readonly unknown[] {
    return this.historyStateListeners;
  }
}

interface WebNavigationDetailsFixture {
  readonly frameId: number;
  readonly tabId: number;
  readonly timeStamp: number;
  readonly transitionQualifiers: readonly string[];
  readonly transitionType: string;
  readonly url: string;
}

interface TabChangeInfoFixture {
  readonly status?: string;
  readonly title?: string;
  readonly url?: string;
}

interface TabFixture {
  readonly id?: number;
  readonly openerTabId?: number;
  readonly title?: string;
  readonly url?: string;
  readonly windowId?: number;
}
