export const SIDE_PANEL_ROUTE_INTENT_STORAGE_KEY = "trail.sidePanel.routeIntent.v1";

export type SidePanelRouteIntent = "end_session" | "recall";

export interface StoredSidePanelRouteIntent {
  readonly route: SidePanelRouteIntent;
  readonly updatedAt: string;
}

export interface SidePanelRouteIntentStorage {
  clear(): Promise<void>;
  read(): Promise<SidePanelRouteIntent | null>;
  write(route: SidePanelRouteIntent): Promise<void>;
}

/**
 * Stores the user's explicit side-panel destination so opening the Chrome side
 * panel is not confused with stale Recall state.
 */
export class ChromeLocalSidePanelRouteIntentStorage implements SidePanelRouteIntentStorage {
  private readonly now: () => Date;

  constructor(now: () => Date = () => new Date()) {
    this.now = now;
  }

  async clear(): Promise<void> {
    await chrome.storage.local.remove(SIDE_PANEL_ROUTE_INTENT_STORAGE_KEY);
  }

  async read(): Promise<SidePanelRouteIntent | null> {
    const values = await chrome.storage.local.get(SIDE_PANEL_ROUTE_INTENT_STORAGE_KEY);
    return parseStoredSidePanelRouteIntent(values[SIDE_PANEL_ROUTE_INTENT_STORAGE_KEY]);
  }

  async write(route: SidePanelRouteIntent): Promise<void> {
    await chrome.storage.local.set({
      [SIDE_PANEL_ROUTE_INTENT_STORAGE_KEY]: {
        route,
        updatedAt: this.now().toISOString()
      }
    });
  }
}

export function parseStoredSidePanelRouteIntent(value: unknown): SidePanelRouteIntent | null {
  if (!isRecord(value) || !isRoute(value.route) || !isRfc3339Instant(value.updatedAt)) {
    return null;
  }

  return value.route;
}

function isRoute(value: unknown): value is SidePanelRouteIntent {
  return value === "end_session" || value === "recall";
}

function isRfc3339Instant(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0 && !Number.isNaN(Date.parse(value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
