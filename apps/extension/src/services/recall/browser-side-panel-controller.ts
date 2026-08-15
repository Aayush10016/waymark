export class BrowserRecallSidePanelController {
  private readonly api: typeof chrome;
  private sourceTabId: number | null = null;
  private sourceWindowId: number | null = null;

  constructor(api: typeof chrome) {
    this.api = api;
  }

  async openResource(url: string): Promise<"blocked" | "opened"> {
    try {
      await this.api.tabs.create({ active: true, url });
      return "opened";
    } catch {
      return "blocked";
    }
  }

  async openForCurrentWindow(): Promise<"blocked" | "opened"> {
    const tab = await this.activeTab();
    this.sourceTabId = tab.id ?? null;
    this.sourceWindowId = tab.windowId ?? null;
    if (tab.windowId !== undefined) {
      try {
        await this.api.sidePanel.open({ windowId: tab.windowId });
        return "opened";
      } catch {
        return "blocked";
      }
    }
    return "blocked";
  }

  async restoreSourceFocus(): Promise<void> {
    if (this.sourceWindowId !== null) {
      await this.api.windows.update(this.sourceWindowId, { focused: true });
    }
    if (this.sourceTabId !== null) {
      await this.api.tabs.update(this.sourceTabId, { active: true });
    }
  }

  private activeTab(): Promise<{ readonly id: number | undefined; readonly windowId: number | undefined }> {
    return new Promise((resolve) => {
      this.api.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        resolve({
          id: tabs[0]?.id,
          windowId: tabs[0]?.windowId
        });
      });
    });
  }
}
