import { afterEach, describe, expect, it, vi } from "vitest";
import { ChromeConsentCompletionNavigator } from "../src/onboarding/consent/consent-completion-navigator";
import type { SidePanelRouteIntent, SidePanelRouteIntentStorage } from "../src/storage/sidepanel";

describe("ChromeConsentCompletionNavigator", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("opens the popup when Chrome permits it, records Recall intent, and closes onboarding", async () => {
    const openPopup = vi.fn(() => Promise.resolve());
    const tabsRemove = vi.fn(() => Promise.resolve());
    const routeIntentStorage = new RecordingSidePanelRouteIntentStorage();
    stubChrome({ openPopup, tabsRemove });

    await new ChromeConsentCompletionNavigator(routeIntentStorage).completeAcceptedConsent();

    expect(openPopup).toHaveBeenCalledOnce();
    expect(tabsRemove).toHaveBeenCalledWith(42);
    await expect(routeIntentStorage.routes()).resolves.toEqual(["recall"]);
  });

  it("falls back to the side panel when popup opening is unavailable", async () => {
    const openPopup = vi.fn(() => Promise.reject(new Error("popup blocked")));
    const sidePanelOpen = vi.fn(() => Promise.resolve());
    const routeIntentStorage = new RecordingSidePanelRouteIntentStorage();
    stubChrome({ openPopup, sidePanelOpen });

    await new ChromeConsentCompletionNavigator(routeIntentStorage).completeAcceptedConsent();

    expect(openPopup).toHaveBeenCalledOnce();
    expect(sidePanelOpen).toHaveBeenCalledWith({ windowId: -2 });
  });
});

function stubChrome(options: {
  readonly openPopup?: () => Promise<void>;
  readonly sidePanelOpen?: (options: { readonly windowId: number }) => Promise<void>;
  readonly tabsRemove?: (tabId: number) => Promise<void>;
}): void {
  vi.stubGlobal("chrome", {
    action: {
      openPopup: options.openPopup
    },
    sidePanel: {
      open: options.sidePanelOpen ?? (() => Promise.resolve())
    },
    tabs: {
      query: () => Promise.resolve([{ id: 42 }]),
      remove: options.tabsRemove ?? (() => Promise.resolve())
    },
    windows: {
      WINDOW_ID_CURRENT: -2
    }
  });
}

class RecordingSidePanelRouteIntentStorage implements SidePanelRouteIntentStorage {
  private readonly routeLog: SidePanelRouteIntent[] = [];

  clear(): Promise<void> {
    this.routeLog.length = 0;
    return Promise.resolve();
  }

  read(): Promise<SidePanelRouteIntent | null> {
    return Promise.resolve(this.routeLog.at(-1) ?? null);
  }

  write(route: SidePanelRouteIntent): Promise<void> {
    this.routeLog.push(route);
    return Promise.resolve();
  }

  routes(): Promise<readonly SidePanelRouteIntent[]> {
    return Promise.resolve(this.routeLog);
  }
}
