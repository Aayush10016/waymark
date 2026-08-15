import {
  ChromeLocalSidePanelRouteIntentStorage,
  type SidePanelRouteIntentStorage
} from "../../storage/sidepanel";

export interface ConsentCompletionNavigator {
  completeAcceptedConsent(): Promise<void>;
}

/**
 * Browser adapter for leaving onboarding after capture consent is accepted.
 */
export class ChromeConsentCompletionNavigator implements ConsentCompletionNavigator {
  private readonly routeIntentStorage: SidePanelRouteIntentStorage;

  constructor(routeIntentStorage: SidePanelRouteIntentStorage = new ChromeLocalSidePanelRouteIntentStorage()) {
    this.routeIntentStorage = routeIntentStorage;
  }

  async completeAcceptedConsent(): Promise<void> {
    await this.routeIntentStorage.write("recall").catch(() => undefined);
    await openTrailSurface();
    await closeCurrentOnboardingTab();
  }
}

async function openTrailSurface(): Promise<void> {
  const action = chrome.action as typeof chrome.action & {
    readonly openPopup?: () => Promise<void>;
  };

  if (typeof action.openPopup === "function") {
    try {
      await action.openPopup();
      return;
    } catch {
      // Chrome only permits popup opening in limited contexts. Fall back below.
    }
  }

  await chrome.sidePanel.open({ windowId: chrome.windows.WINDOW_ID_CURRENT }).catch(() => undefined);
}

async function closeCurrentOnboardingTab(): Promise<void> {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => []);
  const currentTabId = tabs[0]?.id;
  if (currentTabId === undefined) {
    return;
  }

  await chrome.tabs.remove(currentTabId).catch(() => undefined);
}
