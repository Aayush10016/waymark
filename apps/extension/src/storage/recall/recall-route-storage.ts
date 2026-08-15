import { defaultRecallState, parseRecallResponseMessage, recallResponse, type RecallStatePayload } from "@trail/extension-messages";

const RECALL_ROUTE_STORAGE_KEY = "trail.recall.route.v1";

export interface RecallRouteStorage {
  clear(): Promise<void>;
  read(): Promise<RecallStatePayload>;
  write(state: RecallStatePayload): Promise<void>;
}

export class ChromeLocalRecallRouteStorage implements RecallRouteStorage {
  async clear(): Promise<void> {
    await chrome.storage.local.remove(RECALL_ROUTE_STORAGE_KEY);
  }

  async read(): Promise<RecallStatePayload> {
    const stored = await chrome.storage.local.get(RECALL_ROUTE_STORAGE_KEY);
    return parseStoredRecallState(stored[RECALL_ROUTE_STORAGE_KEY]) ?? defaultRecallState();
  }

  async write(state: RecallStatePayload): Promise<void> {
    await chrome.storage.local.set({ [RECALL_ROUTE_STORAGE_KEY]: state });
  }
}

export function parseStoredRecallState(value: unknown): RecallStatePayload | null {
  const parsed = parseRecallResponseMessage({
    schemaVersion: 1,
    type: "RECALL_RESULT",
    requestId: "stored-recall-route",
    source: "background",
    payload: value
  });
  return parsed.ok ? parsed.message.payload : null;
}

export function inMemoryRecallResponsePayload(payload: RecallStatePayload): RecallStatePayload {
  const parsed = parseRecallResponseMessage(
    recallResponse(
      {
        schemaVersion: 1,
        type: "RECALL_GET_STATE",
        requestId: "in-memory-recall-route",
        source: "background",
        payload: {}
      },
      payload
    )
  );
  return parsed.ok ? parsed.message.payload : defaultRecallState();
}
