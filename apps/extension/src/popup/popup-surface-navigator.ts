import {
  ChromeLocalSidePanelRouteIntentStorage,
  type SidePanelRouteIntentStorage
} from "../storage/sidepanel";

export interface PopupSurfaceNavigator {
  openEndSessionPanel(): void;
  openApp(): void;
  openOnboarding(): void;
  openRecallPanel(): void;
}

/**
 * Browser adapter for popup-originated navigation.
 *
 * Side panel opening must be invoked directly from the popup click handler so
 * Chrome preserves the required user gesture.
 */
export class ChromePopupSurfaceNavigator implements PopupSurfaceNavigator {
  private readonly routeIntentStorage: SidePanelRouteIntentStorage;

  constructor(
    routeIntentStorage: SidePanelRouteIntentStorage =
      new ChromeLocalSidePanelRouteIntentStorage()
  ) {
    this.routeIntentStorage = routeIntentStorage;
  }

  openEndSessionPanel(): void {
    void this.routeIntentStorage.write("end_session").catch(() => undefined);
    openSidePanel();
  }

  openApp(): void {
    openExtensionTab("app.html");
  }

  openOnboarding(): void {
    openExtensionTab("onboarding.html");
  }

  openRecallPanel(): void {
    void this.routeIntentStorage.write("recall").catch(() => undefined);
    openSidePanel();
  }
}

function openExtensionTab(path: string): void {
  void chrome.tabs.create({ active: true, url: chrome.runtime.getURL(path) }).catch(() => undefined);
}

function openSidePanel(): void {
  void chrome.sidePanel.open({ windowId: chrome.windows.WINDOW_ID_CURRENT }).catch(() => undefined);
}
