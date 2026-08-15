import {
  PROBLEM_SESSION_MESSAGE_TYPES,
  parseProblemSessionResponseMessage,
  type ProblemSessionMessageType,
  type ProblemSessionRequestMessage,
  type ProblemSessionResponsePayload
} from "@trail/extension-messages";

type PopupProblemSessionMessageType = Exclude<
  ProblemSessionMessageType,
  typeof PROBLEM_SESSION_MESSAGE_TYPES.saveSolution
>;
type EmptyPopupProblemSessionMessageType = Exclude<
  PopupProblemSessionMessageType,
  typeof PROBLEM_SESSION_MESSAGE_TYPES.start
>;

export type PopupProblemSessionClientResult =
  | { readonly payload: ProblemSessionResponsePayload; readonly status: "received" }
  | { readonly status: "unavailable" };

export interface PopupProblemSessionClient {
  dismiss(): Promise<PopupProblemSessionClientResult>;
  finish(): Promise<PopupProblemSessionClientResult>;
  getState(): Promise<PopupProblemSessionClientResult>;
  start(title: string): Promise<PopupProblemSessionClientResult>;
  undoDismiss(): Promise<PopupProblemSessionClientResult>;
}

/**
 * Popup adapter for the background-owned Problem Session message API.
 */
export class RuntimePopupProblemSessionClient implements PopupProblemSessionClient {
  dismiss(): Promise<PopupProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.dismiss);
  }

  finish(): Promise<PopupProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.finish);
  }

  getState(): Promise<PopupProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.get);
  }

  start(title: string): Promise<PopupProblemSessionClientResult> {
    return this.sendStart(title);
  }

  undoDismiss(): Promise<PopupProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.undoDismiss);
  }

  private send(type: EmptyPopupProblemSessionMessageType): Promise<PopupProblemSessionClientResult> {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(createEmptyProblemSessionRequest(type), (response: unknown) => {
        const parsed = parseProblemSessionResponseMessage(response);
        if (!parsed.ok) {
          resolve({ status: "unavailable" });
          return;
        }

        resolve({ payload: parsed.message.payload, status: "received" });
      });
    });
  }

  private sendStart(title: string): Promise<PopupProblemSessionClientResult> {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(createStartProblemSessionRequest(title), (response: unknown) => {
        const parsed = parseProblemSessionResponseMessage(response);
        if (!parsed.ok) {
          resolve({ status: "unavailable" });
          return;
        }

        resolve({ payload: parsed.message.payload, status: "received" });
      });
    });
  }
}

function createEmptyProblemSessionRequest(type: EmptyPopupProblemSessionMessageType): ProblemSessionRequestMessage {
  return {
    schemaVersion: 1,
    type,
    requestId: crypto.randomUUID(),
    source: "popup",
    payload: {}
  };
}

function createStartProblemSessionRequest(title: string): ProblemSessionRequestMessage {
  return {
    schemaVersion: 1,
    type: PROBLEM_SESSION_MESSAGE_TYPES.start,
    requestId: crypto.randomUUID(),
    source: "popup",
    payload: { title }
  };
}
