import { createUrlQuerySanitizer, defaultCaptureSanitationPolicy } from "../services/capture-policy";
import { CaptureControlRuntimeService } from "../services/capture-controls";
import { CaptureConsentRuntimeService } from "../services/consent";
import { NavigationCaptureService, type CaptureEventSink } from "../services/navigation-capture";
import { ChromeLocalCaptureControlStorage } from "../storage/capture-controls";
import { IndexedDbCaptureEventQueue } from "../storage/capture-queue";
import { ChromeLocalCaptureConsentStorage } from "../storage/consent/capture-consent-storage";

type WebNavigationDetails = chrome.webNavigation.WebNavigationTransitionCallbackDetails;

interface TabMetadata {
  readonly openerTabId: number | null;
  readonly title: string | null;
  readonly url: string | null;
  readonly windowId: number | null;
}

interface TabMetadataProvider {
  get(tabId: number): Promise<TabMetadata>;
}

interface NavigationCaptureListenerOptions {
  readonly api: typeof chrome;
  readonly captureService: NavigationCaptureService;
  readonly tabMetadataProvider: TabMetadataProvider;
}

export function createDefaultNavigationCaptureService(): NavigationCaptureService {
  return createDefaultNavigationCaptureServiceWithSink(new IndexedDbCaptureEventQueue());
}

export function createDefaultNavigationCaptureServiceWithSink(sink: CaptureEventSink): NavigationCaptureService {
  return new NavigationCaptureService({
    consentReader: new CaptureConsentRuntimeService({
      storage: new ChromeLocalCaptureConsentStorage()
    }),
    controlReader: new CaptureControlRuntimeService({
      storage: new ChromeLocalCaptureControlStorage()
    }),
    sanitizer: createUrlQuerySanitizer(defaultCaptureSanitationPolicy),
    sink
  });
}

export function installNavigationCaptureListeners(
  api: typeof chrome = chrome,
  captureService: NavigationCaptureService = createDefaultNavigationCaptureService()
): void {
  installNavigationCaptureListenerAdapters({
    api,
    captureService,
    tabMetadataProvider: new ChromeTabMetadataProvider(api)
  });
}

export function installNavigationCaptureListenerAdapters(options: NavigationCaptureListenerOptions): void {
  options.api.webNavigation.onCommitted.addListener((details) => {
    void handleWebNavigationEvent(options, details, "web_navigation_committed");
  });

  options.api.webNavigation.onHistoryStateUpdated.addListener((details) => {
    void handleWebNavigationEvent(options, details, "web_navigation_history_state");
  });

  options.api.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.url === undefined && changeInfo.title === undefined && changeInfo.status !== "complete") {
      return;
    }

    const url = changeInfo.url ?? tab.url;
    if (url === undefined) {
      return;
    }

    void options.captureService.capture({
      frameId: 0,
      openerTabId: tab.openerTabId ?? null,
      source: "tab_updated",
      tabId,
      title: changeInfo.title ?? tab.title ?? null,
      url,
      windowId: tab.windowId ?? null
    });
  });
}

async function handleWebNavigationEvent(
  options: NavigationCaptureListenerOptions,
  details: WebNavigationDetails,
  source: "web_navigation_committed" | "web_navigation_history_state"
): Promise<void> {
  if (details.frameId !== 0) {
    return;
  }

  const tabMetadata = await options.tabMetadataProvider.get(details.tabId);

  await options.captureService.capture({
    browserEventTime: details.timeStamp,
    frameId: details.frameId,
    navigationType: details.transitionType,
    openerTabId: tabMetadata.openerTabId,
    source,
    tabId: details.tabId,
    title: tabMetadata.title,
    transitionQualifiers: details.transitionQualifiers,
    url: details.url,
    windowId: tabMetadata.windowId
  });
}

class ChromeTabMetadataProvider implements TabMetadataProvider {
  private readonly api: typeof chrome;

  constructor(api: typeof chrome) {
    this.api = api;
  }

  get(tabId: number): Promise<TabMetadata> {
    return new Promise((resolve) => {
      this.api.tabs.get(tabId, (tab) => {
        if (this.api.runtime.lastError !== undefined) {
          resolve(emptyTabMetadata());
          return;
        }

        resolve({
          openerTabId: tab.openerTabId ?? null,
          title: tab.title ?? null,
          url: tab.url ?? null,
          windowId: tab.windowId ?? null
        });
      });
    });
  }
}

function emptyTabMetadata(): TabMetadata {
  return {
    openerTabId: null,
    title: null,
    url: null,
    windowId: null
  };
}
