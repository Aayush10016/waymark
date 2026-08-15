import { afterEach, describe, expect, it, vi } from "vitest";
import { ChromePopupSurfaceNavigator } from "../src/popup/popup-surface-navigator";
import type { SidePanelRouteIntent, SidePanelRouteIntentStorage } from "../src/storage/sidepanel";

describe("ChromePopupSurfaceNavigator", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("opens Recall side panel directly from the popup surface and records Recall intent", async () => {
    const sidePanelOpen = vi.fn(() => Promise.resolve());
    stubChrome({ sidePanelOpen });
    const routeIntentStorage = new RecordingSidePanelRouteIntentStorage();

    new ChromePopupSurfaceNavigator(routeIntentStorage).openRecallPanel();

    expect(sidePanelOpen).toHaveBeenCalledWith({ windowId: -2 });
    await expect(routeIntentStorage.routes()).resolves.toEqual(["recall"]);
  });

  it("opens End Session side panel directly from the popup surface and records End Session intent", async () => {
    const sidePanelOpen = vi.fn(() => Promise.resolve());
    stubChrome({ sidePanelOpen });
    const routeIntentStorage = new RecordingSidePanelRouteIntentStorage();

    new ChromePopupSurfaceNavigator(routeIntentStorage).openEndSessionPanel();

    expect(sidePanelOpen).toHaveBeenCalledWith({ windowId: -2 });
    await expect(routeIntentStorage.routes()).resolves.toEqual(["end_session"]);
  });

  it("opens the existing onboarding and app surfaces in extension tabs", () => {
    const tabsCreate = vi.fn(() => Promise.resolve());
    const getUrl = vi.fn((path: string) => `chrome-extension://trail/${path}`);
    stubChrome({ getUrl, tabsCreate });
    const navigator = new ChromePopupSurfaceNavigator();

    navigator.openOnboarding();
    navigator.openApp();

    expect(tabsCreate).toHaveBeenNthCalledWith(1, {
      active: true,
      url: "chrome-extension://trail/onboarding.html"
    });
    expect(tabsCreate).toHaveBeenNthCalledWith(2, {
      active: true,
      url: "chrome-extension://trail/app.html"
    });
  });

  it("handles rejected Chrome navigation promises without unhandled failures", async () => {
    const rejection = Promise.reject(new Error("Chrome rejected navigation."));
    rejection.catch(() => undefined);
    const sidePanelOpen = vi.fn(() => rejection);
    stubChrome({ sidePanelOpen });

    new ChromePopupSurfaceNavigator().openRecallPanel();

    await expect(rejection).rejects.toThrow("Chrome rejected navigation.");
    expect(sidePanelOpen).toHaveBeenCalledOnce();
  });
});

function stubChrome(options: {
  readonly getUrl?: (path: string) => string;
  readonly sidePanelOpen?: (options: { readonly windowId: number }) => Promise<void>;
  readonly tabsCreate?: (options: { readonly active: boolean; readonly url: string }) => Promise<void>;
}): void {
  vi.stubGlobal("chrome", {
    runtime: {
      getURL: options.getUrl ?? ((path: string) => `chrome-extension://trail/${path}`)
    },
    sidePanel: {
      open: options.sidePanelOpen ?? (() => Promise.resolve())
    },
    tabs: {
      create: options.tabsCreate ?? (() => Promise.resolve())
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
