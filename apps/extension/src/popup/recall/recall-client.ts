import {
  RECALL_MESSAGE_TYPES,
  parseRecallResponseMessage,
  type RecallInvokeMessage,
  type RecallStatePayload
} from "@trail/extension-messages";

export type PopupRecallClientResult =
  | {
      readonly payload: RecallStatePayload;
      readonly status: "received";
    }
  | {
      readonly status: "unavailable";
    };

export interface PopupRecallClient {
  invoke(): Promise<PopupRecallClientResult>;
}

export class RuntimePopupRecallClient implements PopupRecallClient {
  invoke(): Promise<PopupRecallClientResult> {
    return sendRecallMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.invoke,
      requestId: crypto.randomUUID(),
      source: "popup",
      payload: {
        source: "popup"
      }
    });
  }
}

function sendRecallMessage(message: RecallInvokeMessage): Promise<PopupRecallClientResult> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (response: unknown) => {
      const parsed = parseRecallResponseMessage(response);
      if (!parsed.ok) {
        resolve({ status: "unavailable" });
        return;
      }

      resolve({ payload: parsed.message.payload, status: "received" });
    });
  });
}

export function recallStatusCopy(result: RecallStatePayload): string {
  switch (result.commandStatus) {
    case "authentication_required":
      return "Reconnect Trail to use Recall.";
    case "matched":
      return "Opening similar problem.";
    case "no_active_problem":
      return "Opening Recall search.";
    case "no_match":
      return "No similar problem found. Opening search.";
    case "not_found":
      return "This problem is no longer available. Opening search.";
    case "opened":
      return "Opening Recall.";
    case "unavailable":
      return "Recall is temporarily unavailable. Opening search.";
  }
}
