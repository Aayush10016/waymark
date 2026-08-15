import {
  RECALL_MESSAGE_TYPES,
  defaultRecallState,
  parseRecallSearchResponseMessage,
  parseRecallResponseMessage,
  type GhostReplayResourcePayload,
  type RecallSearchResultPayload,
  type RecallSearchResultPayloadEnvelope,
  type RecallStatePayload
} from "@trail/extension-messages";

export type SidePanelRecallClientResult =
  | {
      readonly payload: RecallStatePayload;
      readonly status: "received";
    }
  | {
      readonly status: "unavailable";
    };

export interface SidePanelRecallClient {
  getState(): Promise<SidePanelRecallClientResult>;
  openResource(resourceId: string): Promise<SidePanelRecallClientResult>;
  openSolvedProblem(result: RecallSearchResultPayload): Promise<SidePanelRecallClientResult>;
  search(query: string | null): Promise<SidePanelRecallSearchClientResult>;
}

export type SidePanelRecallSearchClientResult =
  | {
      readonly payload: RecallSearchResultPayloadEnvelope;
      readonly status: "received";
    }
  | {
      readonly status: "unavailable";
    };

export class RuntimeSidePanelRecallClient implements SidePanelRecallClient {
  getState(): Promise<SidePanelRecallClientResult> {
    return sendRecallMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.getState,
      requestId: crypto.randomUUID(),
      source: "sidepanel",
      payload: {}
    });
  }

  openResource(resourceId: GhostReplayResourcePayload["id"]): Promise<SidePanelRecallClientResult> {
    return sendRecallMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.openResource,
      requestId: crypto.randomUUID(),
      source: "sidepanel",
      payload: { resourceId }
    });
  }

  openSolvedProblem(result: RecallSearchResultPayload): Promise<SidePanelRecallClientResult> {
    return sendRecallMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.openSolvedProblem,
      requestId: crypto.randomUUID(),
      source: "sidepanel",
      payload: { result }
    });
  }

  search(query: string | null): Promise<SidePanelRecallSearchClientResult> {
    return sendRecallSearchMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.search,
      requestId: crypto.randomUUID(),
      source: "sidepanel",
      payload: { query }
    });
  }
}

function sendRecallMessage(message: Record<string, unknown>): Promise<SidePanelRecallClientResult> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (response: unknown) => {
      const parsed = parseRecallResponseMessage(response);

      if (!parsed.ok) {
        resolve({ status: "unavailable" });
        return;
      }

      resolve({
        payload: parsed.message.payload,
        status: "received"
      });
    });
  });
}

function sendRecallSearchMessage(
  message: Record<string, unknown>
): Promise<SidePanelRecallSearchClientResult> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (response: unknown) => {
      const parsed = parseRecallSearchResponseMessage(response);

      if (!parsed.ok) {
        resolve({ status: "unavailable" });
        return;
      }

      resolve({
        payload: parsed.message.payload,
        status: "received"
      });
    });
  });
}

export function fallbackRecallState(): RecallStatePayload {
  return defaultRecallState();
}
